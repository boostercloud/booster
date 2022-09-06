import { FilterFor, SortFor } from '@boostercloud/framework-types'

export function queryRecordForWithoutValues(filters: FilterFor<unknown>): Record<string, QueryOperation<QueryValue>> {
  return queryRecordFor(filters, '', undefined, false)
}

export function toLocalSortForWithoutValues(sortBy?: SortFor<unknown>): undefined | LocalSortedFor {
  return toLocalSortFor(sortBy, '', undefined, false)
}

/**
 * Creates a query record out of the read mode name and
 * the GraphQL filters, ready to be passed into the `query`
 * method of the read model registry.
 */
export function queryRecordFor(
  filters: FilterFor<unknown>,
  nested?: string,
  queryFromFilters: Record<string, object> = {},
  addValues = true
): Record<string, QueryOperation<QueryValue>> {
  const valuePrefix = addValues ? 'value.' : ''
  if (Object.keys(filters).length != 0) {
    for (const key in filters) {
      const propName = nested ? `${nested}.${key}` : key
      const filter = (filters as FilterFor<any>)[key] as FilterFor<any>
      switch (key) {
        case 'not':
          queryFromFilters[`$${propName}`] = queryRecordFor(filter, '', undefined, addValues)
          break
        case 'or':
        case 'and':
          queryFromFilters[`$${propName}`] = (filters[key] as Array<FilterFor<any>>).map((filter) =>
            queryRecordFor(filter, '', undefined, addValues)
          )
          break
        default:
          if (!Object.keys(queryOperatorTable).includes(Object.keys(filter)[0])) {
            queryRecordFor(filter, propName, queryFromFilters, addValues)
          } else {
            queryFromFilters[`${valuePrefix}${propName}`] = filterToQuery(filter) as FilterFor<QueryValue>
          }
          break
      }
    }
  }
  return { ...queryFromFilters }
}

/**
 * Transforms a GraphQL Booster filter into an neDB query
 */
function filterToQuery(filter: FilterFor<any>): QueryOperation<QueryValue> {
  const [query] = Object.entries(filter).map(([propName, filter]) => {
    const query = queryOperatorTable[propName]
    const queryFilter = Array.isArray(filter) ? filter : [filter]
    return query(queryFilter)
  })
  return query
}

type QueryValue = number | string | boolean
type QueryOperation<TValue> =
  // In the case that the operation is `eq`, NeDB matches directly
  | TValue
  // For these, the value must be single as a result
  | {
      [TKey in '$lt' | '$lte' | '$gt' | '$gte' | '$ne' | '$exists']?: TValue
    }
  // `in` operators must have an array as a result
  | {
      [TKey in '$in' | '$nin']?: Array<TValue>
    }
  // `regex` must have a RegExp as a result
  | {
      [TKey in '$regex' | '$nin']?: RegExp
    }
  // `elemMatch`
  | {
      [TKey in '$elemMatch']?: TValue
    }

/**
 * Table of equivalences between a GraphQL operation and the NeDB
 * query operator.
 *
 * It is needed that we pass the values, because of the special case
 * of `=`, in which the operator is the value itself.
 */
const queryOperatorTable: Record<string, (values: Array<QueryValue>) => QueryOperation<QueryValue>> = {
  eq: (values) => values[0],
  ne: (values) => ({ $ne: values[0] }),
  lt: (values) => ({ $lt: values[0] }),
  gt: (values) => ({ $gt: values[0] }),
  lte: (values) => ({ $lte: values[0] }),
  gte: (values) => ({ $gte: values[0] }),
  in: (values) => ({ $in: values }),
  isDefined: (values) => ({ $exists: values[0] }),
  contains: buildRegexQuery.bind(null, 'contains'),
  beginsWith: buildRegexQuery.bind(null, 'begins-with'),
  includes: buildIncludes.bind(null, 'contains'),
}

function buildIncludes(operation: string, values: Array<QueryValue>): QueryOperation<QueryValue> {
  const matcher = values[0]
  if (typeof matcher === 'string') {
    return { $regex: new RegExp(matcher) }
  }
  return { $elemMatch: matcher }
}

/**
 * Builds a regex out of string GraphQL queries
 */
function buildRegexQuery(operation: string, values: Array<QueryValue>): QueryOperation<QueryValue> {
  const matcher = values[0]
  if (typeof matcher != 'string') {
    throw new Error(`Attempted to perform a ${operation} operation on a non-string`)
  }
  if (operation === 'begins-with') {
    return { $regex: new RegExp(`^${matcher}`) }
  }
  return { $regex: new RegExp(matcher) }
}

export interface LocalSortedFor {
  [key: string]: number
}

export function toLocalSortFor(
  sortBy?: SortFor<unknown>,
  parentKey = '',
  sortedList: LocalSortedFor = {},
  addValues = true
): undefined | LocalSortedFor {
  if (!sortBy || Object.keys(sortBy).length === 0) return
  const elements = sortBy!
  const valuePrefix = addValues ? 'value.' : ''
  Object.entries(elements).forEach(([key, value]) => {
    if (typeof value === 'string') {
      sortedList[`${valuePrefix}${parentKey}${key}`] = value === 'ASC' ? 1 : -1
    } else {
      toLocalSortFor(value as SortFor<unknown>, `${parentKey}${key}.`, sortedList, addValues)
    }
  })
  return sortedList
}
