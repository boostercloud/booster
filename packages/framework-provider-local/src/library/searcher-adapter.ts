import { FilterOld } from '@boostercloud/framework-types'

/**
 * Creates a query record out of the read mode name and
 * the GraphQL filters, ready to be passed into the `query`
 * method of the read model registry.
 */
export function queryRecordFor(
  readModelName: string,
  filters: Record<string, FilterOld<QueryValue>>
): Record<string, QueryOperation<QueryValue>> {
  const queryFromFilters: Record<string, object> = {}
  if (Object.keys(filters).length != 0) {
    for (const key in filters) {
      queryFromFilters[`value.${key}`] = filterToQuery(filters[key]) as object
    }
  }
  return { ...queryFromFilters, typeName: readModelName }
}

/**
 * Transforms a GraphQL Booster filter into an neDB query
 */
function filterToQuery(filter: FilterOld<QueryValue>): QueryOperation<QueryValue> {
  const query = queryOperatorTable[filter.operation]
  return query(filter.values)
}

export type QueryValue = number | string | boolean
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
  '=': (values) => values[0],
  '!=': (values) => ({ $ne: values[0] }),
  '<': (values) => ({ $lt: values[0] }),
  '>': (values) => ({ $gt: values[0] }),
  '<=': (values) => ({ $lte: values[0] }),
  '>=': (values) => ({ $gte: values[0] }),
  in: (values) => ({ $in: values }),
  between: (values) => ({ $gt: values[0], $lte: values[1] }),
  contains: buildRegexQuery.bind(null, 'contains'),
  'not-contains': buildRegexQuery.bind(null, 'not-contains'),
  'begins-with': buildRegexQuery.bind(null, 'begins-with'),
}

/**
 * Builds a regex out of string GraphQL queries
 */
function buildRegexQuery(operation: string, values: Array<QueryValue>): QueryOperation<QueryValue> {
  const matcher = values[0]
  if (typeof matcher != 'string') {
    throw new Error(`Attempted to perform a ${operation} operation on a non-string`)
  }
  if (operation === 'not-contains') {
    // Matching on a string not containing something by using
    // negative lookahead, which JS' regexes support.
    // Check: https://stackoverflow.com/a/406408/3847023
    return { $regex: new RegExp(`^((?!${matcher}).)*$`, 'gm') }
  }
  if (operation === 'begins-with') {
    return { $regex: new RegExp(`^${matcher}`) }
  }
  return { $regex: new RegExp(matcher) }
}
