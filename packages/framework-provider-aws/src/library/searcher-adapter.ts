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
      FilterExpression: buildFilterExpression(filters),
      ExpressionAttributeNames: buildExpressionAttributeNames(filters),
      ExpressionAttributeValues: buildExpressionAttributeValues(filters),
    }
  }
  logger.debug('Running search with the following params: \n', params)

  const result = await dynamoDB.scan(params).promise()

  logger.debug('Search result: ', result.Items)

  return result.Items ?? []
}

function buildFilterExpression(filters: FilterFor<any>, position?: number, depth?: number): string {
  return Object.entries(filters)
    .map(([propName, filter], propIndex) => {
      const propNumber = position ? position : propIndex
      switch (propName) {
        case 'not':
          return `NOT (${buildFilterExpression(filter as FilterFor<any>)})`
        case 'and':
        case 'or':
          return `(${(filter as Array<FilterFor<any>>)
            .map((arrayFilter, index) => buildFilterExpression(arrayFilter, propIndex, index))
            .join(` ${propName} `)})`
        default:
          return buildOperation(propName, filter, propNumber, depth)
      }
    })
    .join(' AND ')
}

function buildOperation(propName: string, filter: Operation<any> = {}, position = 0, depth = 0): string {
  const holder = placeholderBuilderFor(propName)
  return Object.entries(filter)
    .map(([operation, value]) => {
      switch (operation) {
        case 'eq':
          return `#${propName} = ${holder(position, depth)}`
        case 'ne':
          return `#${propName} <> ${holder(position, depth)}`
        case 'lt':
          return `#${propName} < ${holder(position, depth)}`
        case 'gt':
          return `#${propName} > ${holder(position, depth)}`
        case 'gte':
          return `#${propName} >= ${holder(position, depth)}`
        case 'lte':
          return `#${propName} <= ${holder(position, depth)}`
        case 'in':
          return `#${propName} IN (${value
            .map((value: any, subIndex: number) => `${holder(position, depth)}_${subIndex}`)
            .join(',')})`
        case 'between':
          return `#${propName} BETWEEN ${holder(position)} AND ${holder(position + 1)}`
        case 'contains':
          return `contains(#${propName}, ${holder(position, depth)})`
        case 'beginsWith':
          return `begins_with(#${propName}, ${holder(position)})`
        default:
          if (typeof value === 'object') {
            return `#${propName}.${buildOperation(operation, value, position)}`
          }
          throw new InvalidParameterError(`Operator "${operation}" is not supported`)
      }
    })
    .join(' AND ')
}

function placeholderBuilderFor(propName: string): (valueIndex: number, valueSubIndex?: number) => string {
  return (valueIndex: number, valueSubIndex?: number) =>
    `:${propName}_${valueIndex}` + (valueSubIndex ? `_${valueSubIndex}` : '')
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

function buildExpressionAttributeValues(
  filters: FilterFor<any>,
  position?: number,
  depth?: number
): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  Object.entries(filters).forEach(([propName], propIndex) => {
    const propNumber = position ? position : propIndex
    switch (propName) {
      case 'not':
        Object.assign(attributeValues, buildExpressionAttributeValues(filters[propName] as FilterFor<any>))
        break
      case 'and':
      case 'or':
        for (const [index, filter] of (filters[propName] as Array<FilterFor<any>>).entries()) {
          Object.assign(attributeValues, buildExpressionAttributeValues(filter as FilterFor<any>, propIndex, index))
        }
        break
      default:
        Object.assign(attributeValues, buildAttributeValue(propName, filters[propName], propNumber, depth))
        break
    }
  })
  return attributeValues
}

function buildAttributeValue(
  propName: string,
  filter: Operation<any> = {},
  position = 0,
  depth = 0
): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  const holder = placeholderBuilderFor(propName)

  Object.entries(filter).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((element, elementIndex) => {
        attributeValues[`${holder(position, depth)}_${elementIndex}`] = element
      })
    } else if (typeof value === 'object') {
      Object.assign(attributeValues, buildExpressionAttributeValues({ [key]: value }, position))
    } else {
      attributeValues[holder(position, depth)] = value
    }
  })
  return attributeValues
}
