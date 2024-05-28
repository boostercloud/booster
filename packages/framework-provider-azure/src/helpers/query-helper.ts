import { CosmosClient, SqlParameter, SqlQuerySpec } from '@azure/cosmos'
import {
  BoosterConfig,
  FilterFor,
  InvalidParameterError,
  Operation,
  ProjectionFor,
  ReadModelListResult,
  SortFor,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export async function search<TResult>(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  containerName: string,
  filters: FilterFor<unknown>,
  limit?: number | undefined,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false,
  order?: SortFor<unknown>,
  projections: ProjectionFor<unknown> | string = '*'
): Promise<Array<TResult> | ReadModelListResult<TResult>> {
  const logger = getLogger(config, 'query-helper#search')
  const filterExpression = buildFilterExpression(filters)
  const projectionsExpression = buildProjections(projections)
  const queryDefinition = `SELECT ${projectionsExpression} FROM c ${
    filterExpression !== '' ? `WHERE ${filterExpression}` : filterExpression
  }`
  const queryWithOrder = queryDefinition + buildOrderExpression(order)
  let finalQuery = queryWithOrder
  if (paginatedVersion && limit) {
    finalQuery += ` OFFSET ${afterCursor?.id || 0} LIMIT ${limit} `
  } else {
    if (limit) {
      finalQuery += ` OFFSET 0 LIMIT ${limit} `
    }
  }
  const querySpec: SqlQuerySpec = {
    query: finalQuery,
    parameters: buildExpressionAttributeValues(filters),
  }

  logger.debug('Running search with the following params: \n', querySpec)
  let { resources } = await cosmosDb
    .database(config.resourceNames.applicationStack)
    .container(containerName)
    .items.query(querySpec)
    .fetchAll()

  resources = nestProperties(resources)

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
  propName = nested ? `${nested}["${propName}"]` : `c["${propName}"]`
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
        case 'includes':
          return `ARRAY_CONTAINS(${propName}, ${holder(index)}, true)`
        case 'isDefined':
          return value ? `IS_DEFINED(${propName})` : `NOT IS_DEFINED(${propName})`
        case 'regex':
          return `RegexMatch(${propName}, ${holder(index)})`
        case 'iRegex':
          return `RegexMatch(${propName}, ${holder(index)}, 'i')`
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
    } else if (typeof value === 'object' && key !== 'includes' && value !== null) {
      attributeValues = [...attributeValues, ...buildExpressionAttributeValues({ [key]: value }, usedPlaceholders)]
    } else if (key !== 'isDefined') {
      attributeValues.push({
        name: holder(index),
        value: value as {},
      })
    }
  })
  return attributeValues
}

function buildOrderExpression(sortFor: SortFor<unknown> | undefined): string {
  if (!sortFor || !Object.keys(sortFor).length) return ''
  let orderQuery = ' ORDER BY '
  orderQuery += toLocalSortFor(sortFor)?.join(', ')
  return orderQuery
}

function toLocalSortFor(
  sortBy?: SortFor<unknown>,
  parentKey = '',
  sortedList: Array<string> = []
): undefined | Array<string> {
  if (!sortBy || Object.keys(sortBy).length === 0) return
  const elements = sortBy!
  Object.entries(elements).forEach(([key, value]) => {
    if (typeof value === 'string') {
      sortedList.push(`c.${parentKey}${key} ${value}`)
    } else {
      toLocalSortFor(value as SortFor<unknown>, `${parentKey}${key}.`, sortedList)
    }
  })
  return sortedList
}

function buildProjections(projections: ProjectionFor<unknown> | string = '*'): string {
  if (typeof projections !== 'object') {
    return projections
  }

  // Group fields by the root property
  const groupedFields: { [key: string]: string[] } = {}
  Object.values(projections).forEach((field: string) => {
    const root: string = field.split('.')[0]
    if (!groupedFields[root]) {
      groupedFields[root] = []
    }
    groupedFields[root].push(field)
  })

  return Object.keys(groupedFields)
    .map((root: string): string => {
      const fields = groupedFields[root]
      if (root.endsWith('[]')) {
        const arrayRoot: string = root.slice(0, -2)
        const subFields = fields.map((f: string) => f.replace(`${root}.`, 'item.')).join(', ')
        return `ARRAY(SELECT ${subFields} FROM item IN c.${arrayRoot}) AS ${arrayRoot}`
      } else if (fields.length === 1 && !fields[0].includes('.')) {
        // Simple field
        return `c.${fields[0]}`
      } else {
        // Nested object fields
        const nestedFields: { [key: string]: string[] } = {}
        fields.forEach((f: string) => {
          const parts = f.split('.').slice(1)
          if (parts.length > 0) {
            const nestedRoot = parts[0]
            if (!nestedFields[nestedRoot]) {
              nestedFields[nestedRoot] = []
            }
            nestedFields[nestedRoot].push(parts.join('.'))
          }
        })

        return Object.keys(nestedFields)
          .map((nestedRoot: string) => {
            const subFields = nestedFields[nestedRoot].map((f: string) => `c.${root}.${f} AS "${root}.${f}"`).join(', ')
            if (nestedRoot.endsWith('[]')) {
              const arrayNestedRoot = nestedRoot.slice(0, -2)
              const subArrayFields = nestedFields[nestedRoot]
                .map((f: string) => `item.${f.split('.').slice(1).join('.')}`)
                .join(', ')
              return `ARRAY(SELECT ${subArrayFields} FROM item IN c.${root}.${arrayNestedRoot}) AS "${root}.${arrayNestedRoot}"`
            }
            return subFields
          })
          .join(', ')
      }
    })
    .join(', ')
}

function nestProperties(obj: any): any {
  const result = {}

  function setNestedProperty(obj: any, path: string[], value: any): void {
    let current = obj
    for (let i = 0; i < path.length - 1; i++) {
      if (!current[path[i]]) {
        current[path[i]] = {}
      }
      current = current[path[i]]
    }
    current[path[path.length - 1]] = value
  }

  function processObject(input: any, output: any): void {
    for (const key in input) {
      if (Object.prototype.hasOwnProperty.call(input, key)) {
        const value = input[key]
        const keys = key.split('.')
        setNestedProperty(output, keys, value)
      }
    }
  }

  function processArray(arr: any[]): any[] {
    return arr.map((item: any): any => {
      if (Array.isArray(item)) {
        return processArray(item)
      } else if (item !== null && typeof item === 'object') {
        const nestedItem = {}
        processObject(item, nestedItem)
        return nestedItem
      } else {
        return item
      }
    })
  }

  if (Array.isArray(obj)) {
    return processArray(obj)
  } else if (obj !== null && typeof obj === 'object') {
    processObject(obj, result)
  }

  return result
}
