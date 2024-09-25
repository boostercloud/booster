import { CosmosClient, ItemDefinition, SqlParameter, SqlQuerySpec } from '@azure/cosmos'
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

export async function replaceOrDeleteItem(
  cosmosDb: CosmosClient,
  container: string,
  config: BoosterConfig,
  id: string,
  partitionKey: string,
  newValue?: ItemDefinition
): Promise<void> {
  if (newValue) {
    await cosmosDb
      .database(config.resourceNames.applicationStack)
      .container(container)
      .item(id, partitionKey)
      .replace(newValue)
  } else {
    await cosmosDb.database(config.resourceNames.applicationStack).container(container).item(id, partitionKey).delete()
  }
}

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

  logger.debug('Running search with the following params: \n', JSON.stringify(querySpec))
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

  // Preprocess the projections
  const preprocessedProjections = preprocessProjections(projections)

  // Helper function to convert dot notation to square-bracket notation
  const toSquareBracketsNotation = (path: string): string => {
    return path
      .split('.')
      .map((part) => `["${part}"]`)
      .join('')
  }

  // Group fields by the root property
  const groupedFields: { [key: string]: string[] } = {}
  Object.values(preprocessedProjections).forEach((field: string) => {
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
        const subFields = fields
          .map((f: string) => f.replace(`${root}.`, ''))
          .map(toSquareBracketsNotation)
          .map((f: string) => `item${f}`)
          .join(', ')
        return `ARRAY(SELECT ${subFields} FROM item IN c["${arrayRoot}"]) AS ${arrayRoot}`
      } else if (fields.length === 1 && !fields[0].includes('.')) {
        // Simple field
        return `c${toSquareBracketsNotation(fields[0])}`
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
            const subFields = nestedFields[nestedRoot]
              .map((f: string) => `c${toSquareBracketsNotation(`${root}.${f}`)} AS "${root}.${f}"`)
              .join(', ')
            if (nestedRoot.endsWith('[]')) {
              const arrayNestedRoot = nestedRoot.slice(0, -2)
              const subArrayFields = nestedFields[nestedRoot]
                .map((f: string) => {
                  const subFieldParts = f.split('.').slice(1).join('.')
                  return `item${toSquareBracketsNotation(subFieldParts)}`
                })
                .join(', ')
              return `ARRAY(SELECT ${subArrayFields} FROM item IN c${toSquareBracketsNotation(
                `${root}.${arrayNestedRoot}`
              )}) AS "${root}.${arrayNestedRoot}"`
            }
            return subFields
          })
          .join(', ')
      }
    })
    .join(', ')
}

/**
 * Preprocesses the projections to handle nested arrays and objects.
 *
 * @param {ProjectionFor<unknown>} projections - The projections to preprocess.
 * @returns {ProjectionFor<unknown>} - The preprocessed projections.
 */
function preprocessProjections(projections: ProjectionFor<unknown>): ProjectionFor<unknown> {
  const processed = new Set<string>()

  Object.values(projections).forEach((field: string) => {
    const parts = field.split('.')
    const arrayIndices = parts.reduce((acc, part, index) => {
      if (part.endsWith('[]')) acc.push(index)
      return acc
    }, [] as number[])

    if (
      arrayIndices.length === 0 ||
      (arrayIndices[0] === 0 && arrayIndices.length === 1) ||
      (arrayIndices[0] === 1 && arrayIndices.length === 1)
    ) {
      // This block is accessed when one of the following occurs:
      // - No arrays in the projection
      // - Top-level array not followed by another array
      // - Array nested within a top-level property, no arrays follow
      processed.add(field)
    } else {
      // Cases with nested arrays or arrays deeper in the structure
      const processToIndex = arrayIndices[0] === 0 || arrayIndices[0] === 1 ? arrayIndices[1] : arrayIndices[0]
      const processedField = parts.slice(0, processToIndex + 1).join('.')
      processed.add(processedField.slice(0, -2)) // Remove the '[]' from the last part
    }
  })

  // Convert the Set back to the original type of projections
  if (Array.isArray(projections)) {
    return Array.from(processed) as ProjectionFor<unknown>
  } else {
    return Array.from(processed).reduce((acc, field) => {
      ;(acc as any)[field] = field
      return acc
    }, {} as ProjectionFor<unknown>)
  }
}

/**
 * Transforms the flat properties returned by Cosmos DB into a nested structure. For example, the following object:
 *
 * ```json
 * {
 *   "foo.bar": "baz",
 *   "items": [{"qux.quux": "corge"}]
 * }
 * ```
 *
 * is transformed to this:
 *
 * ```json
 * {
 *   "foo": {
 *     "bar": "baz"
 *   },
 *   "items": [
 *     {
 *       "qux": {
 *           "quux": "corge"
 *         }
 *     }
 *   ]
 * }
 * ```
 *
 * @param {any} obj - The object to be nested.
 * @returns {any} - The nested object.
 */
function nestProperties(obj: any): any {
  const result = {}

  /**
   * Sets a nested property on an object.
   * @param {any} obj - The object on which to set the property.
   * @param {string[]} path - The path to the property.
   * @param {any} value - The value to set.
   */
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

  /**
   * Processes an object, nesting its properties.
   * @param {any} input - The object to process.
   * @param {any} output - The object to output.
   */
  function processObject(input: any, output: any): void {
    for (const key in input) {
      if (Object.prototype.hasOwnProperty.call(input, key)) {
        const value = input[key]
        const keys = key.split('.')
        setNestedProperty(output, keys, value)
      }
    }
  }

  /**
   * Processes an array, nesting its properties.
   * @param {any[]} arr - The array to process.
   * @returns {any[]} - The processed array.
   */
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
