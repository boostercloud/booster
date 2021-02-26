import { CosmosClient, SqlParameter, SqlQuerySpec } from '@azure/cosmos'
import { BoosterConfig, FilterOld, Logger, InvalidParameterError } from '@boostercloud/framework-types'

export async function searchReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: Record<string, FilterOld<any>>
): Promise<Array<any>> {
  const querySpec: SqlQuerySpec = {
    query: `SELECT * FROM c ${buildFilterExpression(filters)}`,
    parameters: buildExpressionAttributeValues(filters),
  }
  logger.debug('Running search with the following params: \n', querySpec)

  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .items.query(querySpec)
    .fetchAll()

  logger.debug('Search result: ', resources)

  return resources ?? []
}

function buildFilterExpression(filters: Record<string, FilterOld<any>>): string {
  const filterExpression = Object.entries(filters)
    .map(([propName, filter]) => buildOperation(propName, filter))
    .join(' AND ')
  if (filterExpression !== '') {
    return `WHERE ${filterExpression}`
  }
  return filterExpression
}

function buildOperation(propName: string, filter: FilterOld<any>): string {
  const holder = placeholderBuilderFor(propName)
  switch (filter.operation) {
    case '=':
      return `c["${propName}"] = ${holder(0)}`
    case '!=':
      return `c["${propName}"] <> ${holder(0)}`
    case '<':
      return `c["${propName}"] < ${holder(0)}`
    case '>':
      return `c["${propName}"] > ${holder(0)}`
    case '>=':
      return `c["${propName}"] >= ${holder(0)}`
    case '<=':
      return `c["${propName}"] <= ${holder(0)}`
    case 'in':
      return `c["${propName}"] IN (${filter.values.map((value, index) => holder(index)).join(',')})`
    case 'between':
      return `c["${propName}"] BETWEEN ${holder(0)} AND ${holder(1)}`
    case 'contains':
      return `CONTAINS(c["${propName}"], ${holder(0)})`
    case 'not-contains':
      return `NOT CONTAINS(c["${propName}"], ${holder(0)})`
    case 'begins-with':
      return `STARTSWITH(c["${propName}"], ${holder(0)})`
    default:
      throw new InvalidParameterError(`Operator "${filter.operation}" is not supported`)
  }
}

function placeholderBuilderFor(propName: string): (valueIndex: number) => string {
  return (valueIndex: number) => `@${propName}_${valueIndex}`
}

function buildExpressionAttributeValues(filters: Record<string, FilterOld<any>>): Array<SqlParameter> {
  const attributeValues: Array<SqlParameter> = []
  for (const propName in filters) {
    const filter = filters[propName]
    const holder = placeholderBuilderFor(propName)
    filter.values.forEach((value, index) => {
      attributeValues.push({
        name: holder(index),
        value,
      })
    })
  }
  return attributeValues
}
