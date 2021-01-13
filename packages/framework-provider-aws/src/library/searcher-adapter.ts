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

function buildFilterExpression(filters: FilterFor<any>, depth?: number): string {
  return Object.entries(filters)
    .map(([propName, filter]) => {
      if (propName.toUpperCase() === 'NOT') return `NOT (${buildFilterExpression(filter as FilterFor<any>)})`
      if (['AND', 'OR'].includes(propName.toUpperCase()) && Array.isArray(filter)) {
        return `(${filter
          .map((arrayFilter, index) => buildFilterExpression(arrayFilter, index))
          .join(` ${propName} `)})`
      }
      return buildOperation(propName, filter, depth)
    })
    .join(' AND ')
}

function buildOperation(propName: string, filter: Operation<any> = {}, depth?: number): string {
  const holder = placeholderBuilderFor(`${propName}${depth ? `_${depth}` : ''}`)
  return Object.entries(filter)
    .map(([operation, filter], index) => {
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
          return `#${propName} IN (${filter.map((value: any, subIndex: number) => holder(index, subIndex)).join(',')})`
        case 'between':
          return `#${propName} BETWEEN ${holder(index)} AND ${holder(index + 1)}`
        case 'contains':
          return `contains(#${propName}, ${holder(index)})`
        case 'beginsWith':
          return `begins_with(#${propName}, ${holder(index)})`
        default:
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
    if (propName.toUpperCase() === 'NOT') {
      Object.assign(attributeNames, buildExpressionAttributeNames(filters[propName] as FilterFor<any>))
    } else if (['AND', 'OR'].includes(propName.toUpperCase())) {
      for (const filter of filters[propName] as Array<FilterFor<any>>) {
        Object.assign(attributeNames, buildExpressionAttributeNames(filter as FilterFor<any>))
      }
    } else {
      attributeNames[`#${propName}`] = propName
    }
  }
  return attributeNames
}

function buildExpressionAttributeValues(filters: FilterFor<any>, depth?: number): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  for (const propName in filters) {
    if (propName.toUpperCase() === 'NOT') {
      Object.assign(attributeValues, buildExpressionAttributeValues(filters[propName] as FilterFor<any>))
    } else if (['AND', 'OR'].includes(propName.toUpperCase())) {
      for (const [index, filter] of (filters[propName] as Array<FilterFor<any>>).entries()) {
        Object.assign(attributeValues, buildExpressionAttributeValues(filter as FilterFor<any>, index))
      }
    } else {
      const filter = filters[propName] || {}
      const holder = placeholderBuilderFor(`${propName}${depth ? `_${depth}` : ''}`)
      Object.values(filter).forEach((value, index) => {
        if (Array.isArray(value)) {
          value.forEach((element, elementIndex) => {
            attributeValues[holder(index, elementIndex)] = element
          })
        } else {
          attributeValues[holder(index)] = value
        }
      })
    }
  }
  return attributeValues
}
