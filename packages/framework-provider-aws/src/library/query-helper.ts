/* eslint-disable @typescript-eslint/ban-ts-comment */
import { FilterFor, InvalidParameterError, Operation } from '@boostercloud/framework-types'
import {
  DocumentClient,
  ExpressionAttributeNameMap,
  ExpressionAttributeValueMap,
  ScanInput,
} from 'aws-sdk/clients/dynamodb'
import { DynamoDB } from 'aws-sdk'
import { PromiseResult } from 'aws-sdk/lib/request'

export function buildFilterExpression(filters: FilterFor<unknown>, usedPlaceholders: Array<string> = []): string {
  return Object.entries(filters)
    .map(([propName, filter]) => {
      switch (propName) {
        case 'not':
          return `NOT (${buildFilterExpression(filter as FilterFor<unknown>, usedPlaceholders)})`
        case 'and':
        case 'or':
          return `(${(filter as Array<FilterFor<unknown>>)
            .map((arrayFilter) => buildFilterExpression(arrayFilter, usedPlaceholders))
            .join(` ${propName} `)})`
        default:
          return buildOperation(propName, filter, usedPlaceholders)
      }
    })
    .join(' AND ')
}

function buildOperation(
  propName: string,
  filter: Operation<any> = {},
  usedPlaceholders: Array<string>,
  nested?: string
): string {
  const holder = placeholderBuilderFor(propName, usedPlaceholders)
  propName = nested ? `${nested}.#${propName}` : propName
  return Object.entries(filter)
    .map(([operation, value], index) => {
      switch (operation) {
        case 'eq':
          return `#${propName} = ${holder(index)}`
        case 'ne':
          return `#${propName} <> ${holder(index)}`
        case 'lt':
          return `#${propName} < ${holder(index)}`
        case 'gt':
          return `#${propName} > ${holder(index)}`
        case 'gte':
          return `#${propName} >= ${holder(index)}`
        case 'lte':
          return `#${propName} <= ${holder(index)}`
        case 'in':
          return `#${propName} IN (${value
            .map((value: any, subIndex: number) => `${holder(index, subIndex)}`)
            .join(',')})`
        case 'contains':
          return `contains(#${propName}, ${holder(index)})`
        case 'beginsWith':
          return `begins_with(#${propName}, ${holder(index)})`
        case 'includes':
          return `contains(#${propName}, ${holder(index)})`
        case 'isDefined':
          if (value) {
            return `attribute_exists(#${propName})`
          } else {
            return `attribute_not_exists(#${propName})`
          }
        default:
          if (typeof value === 'object') {
            return buildOperation(operation, value, usedPlaceholders, propName)
          }
          throw new InvalidParameterError(`Operator "${operation}" is not supported`)
      }
    })
    .join(' AND ')
}

function placeholderBuilderFor(
  propName: string,
  usedPlaceholders: Array<string>
): (valueIndex: number, valueSubIndex?: number) => string {
  return (valueIndex: number, valueSubIndex?: number) => {
    const placeholder = `:${propName}_${valueIndex}` + (typeof valueSubIndex === 'number' ? `_${valueSubIndex}` : '')
    if (usedPlaceholders.includes(placeholder)) return placeholderBuilderFor(propName, usedPlaceholders)(valueIndex + 1)
    usedPlaceholders.push(placeholder)
    return placeholder
  }
}

export function buildExpressionAttributeNames(filters: FilterFor<unknown>): ExpressionAttributeNameMap {
  const attributeNames: ExpressionAttributeNameMap = {}
  for (const propName in filters) {
    switch (propName) {
      case 'not':
        Object.assign(attributeNames, buildExpressionAttributeNames(filters[propName] as FilterFor<unknown>))
        break
      case 'and':
      case 'or':
        for (const filter of filters[propName] as Array<FilterFor<unknown>>) {
          Object.assign(attributeNames, buildExpressionAttributeNames(filter as FilterFor<unknown>))
        }
        break
      case 'includes':
      case 'isDefined':
        // Avoid the default behaviour
        break
      default:
        // @ts-ignore
        Object.entries(filters[propName] as FilterFor<unknown>).forEach(([prop, value]) => {
          attributeNames[`#${propName}`] = propName
          if (typeof value === 'object' && !Array.isArray(value) && value !== null) {
            Object.assign(attributeNames, buildExpressionAttributeNames({ [prop]: value }))
          }
        })
        break
    }
  }
  return attributeNames
}

export function buildExpressionAttributeValues(
  filters: FilterFor<unknown>,
  usedPlaceholders: Array<string> = []
): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  Object.entries(filters).forEach(([propName]) => {
    switch (propName) {
      case 'not':
        Object.assign(
          attributeValues,
          buildExpressionAttributeValues(filters[propName] as FilterFor<unknown>, usedPlaceholders)
        )
        break
      case 'and':
      case 'or':
        for (const filter of filters[propName] as Array<FilterFor<unknown>>) {
          Object.assign(attributeValues, buildExpressionAttributeValues(filter as FilterFor<any>, usedPlaceholders))
        }
        break
      default:
        // @ts-ignore
        Object.assign(attributeValues, buildAttributeValue(propName, filters[propName], usedPlaceholders))
        break
    }
  })
  return attributeValues
}

function buildAttributeValue(
  propName: string,
  filter: Operation<any> = {},
  usedPlaceholders: Array<string>
): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  const holder = placeholderBuilderFor(propName, usedPlaceholders)

  Object.entries(filter).forEach(([key, value], index) => {
    if (Array.isArray(value)) {
      value.forEach((element, subIndex) => {
        attributeValues[holder(index, subIndex)] = element
      })
    } else if (typeof value === 'object' && key !== 'includes' && value !== null) {
      Object.assign(attributeValues, buildExpressionAttributeValues({ [key]: value }, usedPlaceholders))
    } else if (key !== 'isDefined') {
      attributeValues[holder(index)] = value
    }
  })
  return attributeValues
}

/**
 * AWS limits scans to 1Mb. This causes a scan to return no result but a LastEvaluatedKey value.
 * In this case it is necessary to keep querying until the LastEvaluatedKey is undefined which means that
 * the whole table has been scanned.
 *
 * Each time the query is performed, the limit will be used as the maximum number of items to be returned, so it is possible to
 * obtain the following results with a limit of 10:
 *  0 elements, 4 elements, 10 elements.
 * This will cause the results to contain 14 elements when the query was for 10 elements.
 *
 * There are two ways to do this
 *  a) Change the limit to limit - count. This way the limit will always be the number of elements the query needs.
 *  The problem with this option is that the limit will be decreasing each time and the number of queries to scan
 *  the full table could be enormous.
 *  b) Keep the limit to the original (this option is the implemented option). This way we can have more rows than expected.
 *  expected. In this case we have to fix the results.
 *
 * @param dynamoDB
 * @param params
 */
export async function paginatedScan(
  dynamoDB: DynamoDB.DocumentClient,
  params: ScanInput
): Promise<PromiseResult<any, any>> {
  let lastEvaluatedKey: DocumentClient.Key | undefined
  const results: DocumentClient.ItemList = []
  let count = 0
  const limit: number = params.Limit ? params.Limit : 0
  do {
    const result = await dynamoDB.scan(params).promise()
    if (result) {
      if (result?.Items && result?.Items.length > 0) {
        results.push(...result.Items)
      }
      if (result?.Count) {
        count += result.Count
      }
    }
    lastEvaluatedKey = result?.LastEvaluatedKey
    params.ExclusiveStartKey = lastEvaluatedKey
  } while (lastEvaluatedKey && count < limit)
  return buildFixedResult(results, limit, lastEvaluatedKey)
}

function buildFixedResult(
  results: DocumentClient.ItemList,
  limit: number,
  lastEvaluatedKey: DocumentClient.Key | undefined
): PromiseResult<any, any> {
  const items = results.slice(0, limit)
  if (items) {
    const lastItem = items[items.length - 1]
    if (lastEvaluatedKey && lastItem) {
      Object.keys(lastEvaluatedKey).forEach((key) => {
        lastEvaluatedKey[key] = lastItem[key]
      })
    }
  }
  return {
    Items: items,
    Count: items.length,
    LastEvaluatedKey: lastEvaluatedKey,
  }
}
