import { EntityInterface, Filter, BoosterConfig, Logger, InvalidParameterError } from '@boostercloud/framework-types'
import { DynamoDB } from 'aws-sdk'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'
import ExpressionAttributeValueMap = DocumentClient.ExpressionAttributeValueMap

export async function searchEntity(
  dynamoDB: DynamoDB.DocumentClient,
  config: BoosterConfig,
  logger: Logger,
  entityTypeName: string,
  filters: Record<string, Filter<any>>
): Promise<Array<EntityInterface>> {
  const filterExpression = buildFilterExpression(filters)
  const filterExpressionValues = buildExpressionAttributeValues(filters)
  logger.debug(
    'Running search with filter expression = "',
    filterExpression,
    '" and expression values = "',
    filterExpressionValues
  )

  const result = await dynamoDB
    .scan({
      TableName: config.resourceNames.eventsStore,
      ConsistentRead: true,
      ProjectionExpression: 'value',
      FilterExpression: buildFilterExpression(filters),
      ExpressionAttributeValues: buildExpressionAttributeValues(filters),
    })
    .promise()

  logger.debug('Search result: ', result.Items)

  return result.Items as Array<EntityInterface>
}

function buildFilterExpression(filters: Record<string, Filter<any>>): string {
  return Object.entries(filters)
    .map(([propName, filter]) => buildOperation(propName, filter))
    .join(' AND ')
}

function buildOperation(propName: string, filter: Filter<any>): string {
  const holder = placeholderBuilderFor(propName)
  switch (filter.operation) {
    case '=':
      return `${propName} = ${holder(0)}`
    case '!=':
      return `${propName} <> ${holder(0)}`
    case '<':
      return `${propName} < ${holder(0)}`
    case '>':
      return `${propName} > ${holder(0)}`
    case '>=':
      return `${propName} >= ${holder(0)}`
    case '<=':
      return `${propName} <= ${holder(0)}`
    case 'in':
      return `${propName} IN (${filter.values.map((value, index) => holder(index)).join(',')})`
    case 'between':
      return `${propName} BETWEEN ${holder(0)} AND ${holder(1)}`
    case 'contains':
      return `contains(${propName}, ${holder(0)})`
    case 'not-contains':
      return `NOT contains(${propName}, ${holder(0)})`
    case 'begins-with':
      return `begin_with(${propName}, ${holder(0)})`
    default:
      throw new InvalidParameterError(`Operator "${filter.operation}" is not supported`)
  }
}

function placeholderBuilderFor(propName: string): (valueIndex: number) => string {
  return (valueIndex: number) => `:${propName}_${valueIndex}`
}

function buildExpressionAttributeValues(filters: Record<string, Filter<any>>): ExpressionAttributeValueMap {
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
