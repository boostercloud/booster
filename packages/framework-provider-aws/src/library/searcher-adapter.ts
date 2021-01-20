/* eslint-disable @typescript-eslint/no-explicit-any */
import { Operation, FilterFor, BoosterConfig, Logger, InvalidParameterError } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import ExpressionAttributeValueMap = DocumentClient.ExpressionAttributeValueMap
import ExpressionAttributeNameMap = DocumentClient.ExpressionAttributeNameMap

export async function searchReadModel(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<any>
): Promise<Array<any>> {
  let params: DocumentClient.ScanInput = {
    TableName: config.resourceNames.forReadModel(readModelName),
    ConsistentRead: true,
  }
  if (filters && Object.keys(filters).length > 0) {
    params = {
      ...params,
      ...buildExpression(filters),
    }
  }
  logger.debug('Running search with the following params: \n', params)

  const result = await dynamoDB.scan(params).promise()

  logger.debug('Search result: ', result.Items)

  return result.Items ?? []
}

function buildExpression(filters: FilterFor<any>): any {
  const expresion = buildFilterExpression(filters)
  const names = buildExpressionAttributeNames(filters)
  resetAttrCount()
  const values = buildExpressionAttributeValues(filters)
  resetAttrCount()
  return {
    FilterExpression: expresion,
    ExpressionAttributeNames: names,
    ExpressionAttributeValues: values,
  }
}

function buildFilterExpression(filters: FilterFor<any>): string {
  return Object.entries(filters)
    .map(([propName, filter]) => {
      switch (propName) {
        case 'not':
          return `NOT (${buildFilterExpression(filter as FilterFor<any>)})`
        case 'and':
        case 'or':
          return `(${(filter as Array<FilterFor<any>>)
            .map((arrayFilter) => buildFilterExpression(arrayFilter))
            .join(` ${propName} `)})`
        default:
          return buildOperation(propName, filter)
      }
    })
    .join(' AND ')
}

function buildOperation(propName: string, filter: Operation<any> = {}): string {
  const holder = placeholderBuilderFor(propName)
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
        default:
          if (typeof value === 'object') {
            return `#${propName}.${buildOperation(operation, value)}`
          }
          throw new InvalidParameterError(`Operator "${operation}" is not supported`)
      }
    })
    .join(' AND ')
}

const attrCount: string[] = []
function resetAttrCount(): void {
  attrCount.length = 0
}
function placeholderBuilderFor(propName: string): (valueIndex: number, valueSubIndex?: number) => string {
  return (valueIndex: number, valueSubIndex?: number) => {
    const placeholder = `:${propName}_${valueIndex}` + (typeof valueSubIndex === 'number' ? `_${valueSubIndex}` : '')
    if (attrCount.includes(placeholder)) return placeholderBuilderFor(propName)(valueIndex + 1)
    attrCount.push(placeholder)
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
      default:
        Object.entries(filters[propName] as FilterFor<any>).forEach(([prop, value]) => {
          attributeNames[`#${propName}`] = propName
          if (typeof value === 'object') {
            Object.assign(attributeNames, buildExpressionAttributeNames({ [prop]: value }))
          }
        })
        break
    }
  }
  return attributeNames
}

function buildExpressionAttributeValues(filters: FilterFor<any>): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  Object.entries(filters).forEach(([propName]) => {
    switch (propName) {
      case 'not':
        Object.assign(attributeValues, buildExpressionAttributeValues(filters[propName] as FilterFor<any>))
        break
      case 'and':
      case 'or':
        for (const filter of filters[propName] as Array<FilterFor<any>>) {
          Object.assign(attributeValues, buildExpressionAttributeValues(filter as FilterFor<any>))
        }
        break
      default:
        Object.assign(attributeValues, buildAttributeValue(propName, filters[propName]))
        break
    }
  })
  return attributeValues
}

function buildAttributeValue(propName: string, filter: Operation<any> = {}): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  const holder = placeholderBuilderFor(propName)

  Object.entries(filter).forEach(([key, value], index) => {
    if (Array.isArray(value)) {
      value.forEach((element, subIndex) => {
        attributeValues[holder(index, subIndex)] = element
      })
    } else if (typeof value === 'object') {
      Object.assign(attributeValues, buildExpressionAttributeValues({ [key]: value }))
    } else {
      attributeValues[holder(index)] = value
    }
  })
  return attributeValues
}
