/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  BoosterConfig,
  FilterFor,
  InvalidParameterError,
  Operation,
  ReadModelListResult,
  SortFor,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import ExpressionAttributeValueMap = DocumentClient.ExpressionAttributeValueMap
import ExpressionAttributeNameMap = DocumentClient.ExpressionAttributeNameMap

export async function searchReadModel(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortBy?: SortFor<unknown>,
  limit?: number,
  afterCursor?: DynamoDB.DocumentClient.Key | undefined,
  paginatedVersion = false
): Promise<Array<any> | ReadModelListResult<any>> {
  const logger = getLogger(config, 'read-model-searcher-adapter#searchReadModel')
  if (sortBy) {
    logger.info('SortBy not implemented for AWS provider. It will be ignored')
  }
  let params: DocumentClient.ScanInput = {
    TableName: config.resourceNames.forReadModel(readModelName),
    ConsistentRead: true,
    Limit: limit,
    ExclusiveStartKey: afterCursor,
  }
  if (filters && Object.keys(filters).length > 0) {
    params = {
      ...params,
      FilterExpression: buildFilterExpression(filters),
      ExpressionAttributeNames: buildExpressionAttributeNames(filters),
    }
    const expressionAttributeValues = buildExpressionAttributeValues(filters)
    if (expressionAttributeValues && Object.keys(expressionAttributeValues).length > 0) {
      params = {
        ...params,
        ExpressionAttributeValues: expressionAttributeValues,
      }
    }
  }

  logger.debug('Running search with the following params: \n', params)

  const result = await dynamoDB.scan(params).promise()

  logger.debug('Search result: ', result)

  if (paginatedVersion) {
    return {
      items: result.Items ?? [],
      count: result.Count,
      cursor: result.LastEvaluatedKey,
    }
  } else {
    return result.Items ?? []
  }
}

function buildFilterExpression(filters: FilterFor<any>, usedPlaceholders: Array<string> = []): string {
  return Object.entries(filters)
    .map(([propName, filter]) => {
      switch (propName) {
        case 'not':
          return `NOT (${buildFilterExpression(filter as FilterFor<any>, usedPlaceholders)})`
        case 'and':
        case 'or':
          return `(${(filter as Array<FilterFor<any>>)
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

function buildExpressionAttributeNames(filters: FilterFor<any>): ExpressionAttributeNameMap {
  const attributeNames: ExpressionAttributeNameMap = {}
  for (const propName in filters) {
    switch (propName) {
      case 'not':
        Object.assign(attributeNames, buildExpressionAttributeNames(filters[propName] as FilterFor<any>))
        break
      case 'and':
      case 'or':
        for (const filter of filters[propName] as Array<FilterFor<any>>) {
          Object.assign(attributeNames, buildExpressionAttributeNames(filter as FilterFor<any>))
        }
        break
      case 'includes':
      case 'isDefined':
        // Avoid the default behaviour
        break
      default:
        Object.entries(filters[propName] as FilterFor<any>).forEach(([prop, value]) => {
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

function buildExpressionAttributeValues(
  filters: FilterFor<any>,
  usedPlaceholders: Array<string> = []
): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  Object.entries(filters).forEach(([propName]) => {
    switch (propName) {
      case 'not':
        Object.assign(
          attributeValues,
          buildExpressionAttributeValues(filters[propName] as FilterFor<any>, usedPlaceholders)
        )
        break
      case 'and':
      case 'or':
        for (const filter of filters[propName] as Array<FilterFor<any>>) {
          Object.assign(attributeValues, buildExpressionAttributeValues(filter as FilterFor<any>, usedPlaceholders))
        }
        break
      default:
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
