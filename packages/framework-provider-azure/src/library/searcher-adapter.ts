import { CosmosClient, SqlParameter, SqlQuerySpec } from '@azure/cosmos'
import { BoosterConfig, Logger, InvalidParameterError, FilterFor, Operation } from '@boostercloud/framework-types'

export async function searchReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>
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

function buildFilterExpression(filters: FilterFor<any>): string {
  const filterExpression = Object.entries(filters)
    .map(([propName, filter]) => buildOperation(propName, filter))
    .join(' AND ')
  if (filterExpression !== '') {
    return `WHERE ${filterExpression}`
  }
  return filterExpression
}

function buildOperation(propName: string, filter: Operation<any> = {}): string {
  const holder = placeholderBuilderFor(propName)
  return Object.entries(filter)
    .map(([operation, value], index) => {
      switch (operation) {
        case 'eq':
          return `c["${propName}"] = ${holder(index)}`
        case 'ne':
          return `c["${propName}"] <> ${holder(index)}`
        case 'lt':
          return `c["${propName}"] < ${holder(index)}`
        case 'gt':
          return `c["${propName}"] > ${holder(index)}`
        case 'gte':
          return `c["${propName}"] >= ${holder(index)}`
        case 'lte':
          return `c["${propName}"] <= ${holder(index)}`
        case 'in':
          return `c["${propName}"] IN (${value
            .map((value: any, subIndex: number) => holder(index, subIndex))
            .join(',')})`
        case 'contains':
          return `CONTAINS(c["${propName}"], ${holder(index)})`
        case 'begins-with':
          return `STARTSWITH(c["${propName}"], ${holder(index)})`
        default:
          throw new InvalidParameterError(`Operator "${operation}" is not supported`)
      }
    })
    .join(' AND ')
}

function placeholderBuilderFor(propName: string): (valueIndex: number, valueSubIndex?: number) => string {
  return (valueIndex: number, valueSubIndex?: number) =>
    `@${propName}_${valueIndex}` + (typeof valueSubIndex === 'number' ? `_${valueSubIndex}` : '')
}

function buildExpressionAttributeValues(filters: FilterFor<any>): Array<SqlParameter> {
  const attributeValues: Array<SqlParameter> = []
  Object.entries(filters).forEach(([propName]) => {
    const filter = filters[propName]
    const holder = placeholderBuilderFor(propName)
    Object.values(filter as any).forEach((value, index) => {
      attributeValues.push({
        name: holder(index),
        value: value as {},
      })
    })
  })
  return attributeValues
}
