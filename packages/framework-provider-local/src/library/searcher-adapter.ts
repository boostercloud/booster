import { FilterFor } from '@boostercloud/framework-types'

/**
 * Creates a query record out of the read mode name and
 * the GraphQL filters, ready to be passed into the `query`
 * method of the read model registry.
 */
export function queryRecordFor(
  readModelName: string,
  filters: FilterFor<any>
): Record<string, QueryOperation<QueryValue>> {
  const queryFromFilters: Record<string, object> = {}
  if (Object.keys(filters).length != 0) {
    for (const key in filters) {
      switch (key) {
        case 'not':
          queryFromFilters[`$${key}`] = queryRecordFor(readModelName, filters[key] as FilterFor<any>)
          break
        case 'or':
        case 'and':
          queryFromFilters[`$${key}`] = (filters[key] as Array<FilterFor<any>>).map((filter) =>
            queryRecordFor(readModelName, filter)
          )
          break
        default:
          queryFromFilters[`value.${key}`] = filterToQuery(filters[key] as FilterFor<any>) as FilterFor<QueryValue>
          break
      }
    }
  }
  return { ...queryFromFilters, typeName: readModelName }
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
      [TKey in '$lt' | '$lte' | '$gt' | '$gte' | '$ne']?: TValue
    }
  // `in` operators must have an array as a result
  | {
      [TKey in '$in' | '$nin']?: Array<TValue>
    }
  // `regex` must have a RegExp as a result
  | {
      [TKey in '$regex' | '$nin']?: RegExp
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
  contains: buildRegexQuery.bind(null, 'contains'),
  beginsWith: buildRegexQuery.bind(null, 'begins-with'),
  includes: buildRegexQuery.bind(null, 'contains'),
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
