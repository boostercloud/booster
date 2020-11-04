import { FilterOld, BoosterConfig, Logger, InvalidParameterError } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import ExpressionAttributeValueMap = DocumentClient.ExpressionAttributeValueMap
import ExpressionAttributeNameMap = DocumentClient.ExpressionAttributeNameMap

export async function searchReadModel(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: Record<string, FilterOld<any>>
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

function buildFilterExpression(filters: Record<string, FilterOld<any>>): string {
  return Object.entries(filters)
    .map(([propName, filter]) => buildOperation(propName, filter))
    .join(' AND ')
}

function buildOperation(propName: string, filter: FilterOld<any>): string {
  const holder = placeholderBuilderFor(propName)
  switch (filter.operation) {
    case '=':
      return `#${propName} = ${holder(0)}`
    case '!=':
      return `#${propName} <> ${holder(0)}`
    case '<':
      return `#${propName} < ${holder(0)}`
    case '>':
      return `#${propName} > ${holder(0)}`
    case '>=':
      return `#${propName} >= ${holder(0)}`
    case '<=':
      return `#${propName} <= ${holder(0)}`
    case 'in':
      return `#${propName} IN (${filter.values.map((value, index) => holder(index)).join(',')})`
    case 'between':
      return `#${propName} BETWEEN ${holder(0)} AND ${holder(1)}`
    case 'contains':
      return `contains(#${propName}, ${holder(0)})`
    case 'not-contains':
      return `NOT contains(#${propName}, ${holder(0)})`
    case 'begins-with':
      return `begins_with(#${propName}, ${holder(0)})`
    default:
      throw new InvalidParameterError(`Operator "${filter.operation}" is not supported`)
  }
}

function placeholderBuilderFor(propName: string): (valueIndex: number) => string {
  return (valueIndex: number) => `:${propName}_${valueIndex}`
}

function buildExpressionAttributeNames(filters: Record<string, FilterOld<any>>): ExpressionAttributeNameMap {
  const attributeNames: ExpressionAttributeNameMap = {}
  for (const propName in filters) {
    attributeNames[`#${propName}`] = propName
  }
  return attributeNames
}

function buildExpressionAttributeValues(filters: Record<string, FilterOld<any>>): ExpressionAttributeValueMap {
  const attributeValues: ExpressionAttributeValueMap = {}
  for (const propName in filters) {
    const filter = filters[propName]
    const holder = placeholderBuilderFor(propName)
    filter.values.forEach((value, index) => {
      attributeValues[holder(index)] = value
    })
  }
  return attributeValues
}
