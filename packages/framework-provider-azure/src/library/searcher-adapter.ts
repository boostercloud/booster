import { CosmosClient, SqlParameter, SqlQuerySpec } from '@azure/cosmos'
import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  FilterFor,
  Operation,
  ReadModelListResult,
  SortFor,
} from '@boostercloud/framework-types'

export async function searchReadModel(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortByList?: Array<SortFor>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false
): Promise<Array<any> | ReadModelListResult<any>> {
  const filterExpression = buildFilterExpression(filters)
  const queryDefinition = `SELECT * FROM c ${filterExpression !== '' ? `WHERE ${filterExpression}` : filterExpression}`
  const queryWithPagination =
    queryDefinition + (paginatedVersion && limit ? ` OFFSET ${afterCursor?.id || 0} LIMIT ${limit}` : '')
  const querySpec: SqlQuerySpec = {
    query: queryWithPagination,
    parameters: buildExpressionAttributeValues(filters),
  }
  logger.debug('Running search with the following params: \n', querySpec)
  const { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(config.resourceNames.forReadModel(readModelName))
    .items.query(querySpec)
    .fetchAll()

  if (paginatedVersion) {
    return {
      items: resources ?? [],
      count: resources.length,
      cursor: { id: ((limit ? limit : 1) + (afterCursor?.id ? parseInt(afterCursor?.id) : 0)).toString() },
    }
  } else {
    return resources ?? []
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
  propName = nested ? `${nested}.c["${propName}"]` : `c["${propName}"]`
  return Object.entries(filter)
    .map(([operation, value], index) => {
      switch (operation) {
        case 'eq':
          return `${propName} = ${holder(index)}`
        case 'ne':
          return `${propName} <> ${holder(index)}`
        case 'lt':
          return `${propName} < ${holder(index)}`
        case 'gt':
          return `${propName} > ${holder(index)}`
        case 'gte':
          return `${propName} >= ${holder(index)}`
        case 'lte':
          return `${propName} <= ${holder(index)}`
        case 'in':
          return `${propName} IN (${value.map((value: any, subIndex: number) => holder(index, subIndex)).join(',')})`
        case 'contains':
          return `CONTAINS(${propName}, ${holder(index)})`
        case 'beginsWith':
          return `STARTSWITH(${propName}, ${holder(index)})`
        case 'includes': {
          return `ARRAY_CONTAINS(${propName}, ${holder(index)}, true)`
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
    const placeholder = `@${propName}_${valueIndex}` + (typeof valueSubIndex === 'number' ? `_${valueSubIndex}` : '')
    if (usedPlaceholders.includes(placeholder)) return placeholderBuilderFor(propName, usedPlaceholders)(valueIndex + 1)
    usedPlaceholders.push(placeholder)
    return placeholder
  }
}

function buildExpressionAttributeValues(
  filters: FilterFor<any>,
  usedPlaceholders: Array<string> = []
): Array<SqlParameter> {
  let attributeValues: Array<SqlParameter> = []
  Object.entries(filters).forEach(([propName]) => {
    switch (propName) {
      case 'not':
        attributeValues = [
          ...attributeValues,
          ...buildExpressionAttributeValues(filters[propName] as FilterFor<any>, usedPlaceholders),
        ]
        break
      case 'and':
      case 'or':
        for (const filter of filters[propName] as Array<FilterFor<any>>) {
          attributeValues = [
            ...attributeValues,
            ...buildExpressionAttributeValues(filter as FilterFor<any>, usedPlaceholders),
          ]
        }
        break
      default:
        attributeValues = [...attributeValues, ...buildAttributeValue(propName, filters[propName], usedPlaceholders)]
        break
    }
  })
  return attributeValues
}

function buildAttributeValue(
  propName: string,
  filter: Operation<any> = {},
  usedPlaceholders: Array<string>
): Array<SqlParameter> {
  let attributeValues: Array<SqlParameter> = []
  const holder = placeholderBuilderFor(propName, usedPlaceholders)
  Object.entries(filter).forEach(([key, value], index) => {
    if (Array.isArray(value)) {
      value.forEach((element, subIndex) => {
        attributeValues.push({
          name: holder(index, subIndex),
          value: element,
        })
      })
    } else if (typeof value === 'object' && key !== 'includes') {
      attributeValues = [...attributeValues, ...buildExpressionAttributeValues({ [key]: value }, usedPlaceholders)]
    } else {
      attributeValues.push({
        name: holder(index),
        value: value as {},
      })
    }
  })
  return attributeValues
}
