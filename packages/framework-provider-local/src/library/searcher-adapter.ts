import { Filter } from '@boostercloud/framework-types'

/**
 * Transforms a GraphQL Booster filter into an neDB query
 */
export function filterToQuery(filter: Filter<QueryValue>): QueryOperation<QueryValue> {
  const query = queryOperatorTable[filter.operation]
  return query(filter.values)
}

export type QueryValue = number | string | boolean
type QueryOperation<TValue> =
  | TValue
  | {
      [TKey in '$lt' | '$lte' | '$gt' | '$gte' | '$ne']?: TValue
    }
  | {
      [TKey in '$in' | '$nin']?: Array<TValue>
    }
  | {
      [TKey in '$regex' | '$nin']?: RegExp
    }

const queryOperatorTable: Record<string, (vals: Array<QueryValue>) => QueryOperation<QueryValue>> = {
  '=': (val) => val[0],
  '!=': (val) => ({ $ne: val[0] }),
  '<': (val) => ({ $lt: val[0] }),
  '>': (val) => ({ $gt: val[0] }),
  '<=': (val) => ({ $lte: val[0] }),
  '>=': (val) => ({ $gte: val[0] }),
  in: (val) => ({ $in: val }),
  between: (val) => ({ $gt: val[0], $lte: val[1] }),
  contains: buildRegexQuery.bind(null, 'contains'),
  'not-contains': buildRegexQuery.bind(null, 'not-contains'),
  'begins-with': buildRegexQuery.bind(null, 'begins-with'),
}

function buildRegexQuery(operation: string, vals: Array<QueryValue>): QueryOperation<QueryValue> {
  let matcher = vals[0]
  if (typeof matcher != 'string') {
    throw new Error(`Attempted to perform a ${operation} operation on a non-string`)
  }
  if (operation === 'not-contains') {
    throw new Error('not-contains not implemented yet')
  }
  if (operation === 'begins-with') {
    matcher = `^${matcher}`
  }
  return { $regex: new RegExp(matcher) }
}
