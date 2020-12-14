import {
  BoosterConfig,
  Filter,
  Logger,
  ReadModelEnvelope,
  ReadModelInterface,
  UUID,
} from '@boostercloud/framework-types'
import { ReadModelRegistry } from '../services/read-model-registry'

export async function rawReadModelEventsToEnvelopes(
  config: BoosterConfig,
  logger: Logger,
  rawEvents: Array<unknown>
): Promise<Array<ReadModelEnvelope>> {
  return rawEvents as Array<ReadModelEnvelope>
}

export async function fetchReadModel(
  db: ReadModelRegistry,
  _config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModelID: UUID
): Promise<ReadModelInterface> {
  const response = await db.query({ typeName: readModelName, value: { id: readModelID } })
  const item = response[0]
  if (!item) {
    console.log(`[ReadModelAdapter#fetchReadModel] Read model ${readModelName} with ID ${readModelID} not found`)
  } else {
    logger.debug(
      `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
      item.value
    )
  }
  return item?.value
}

export async function storeReadModel(
  db: ReadModelRegistry,
  _config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> {
  await db.store({ typeName: readModelName, value: readModel })
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}

export async function searchReadModel(
  db: ReadModelRegistry,
  _config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: Record<string, Filter<QueryValue>>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<Array<any>> {
  const queryFromFilters: Record<string, object> = {}
  if (Object.keys(filters).length != 0) {
    for (const key in filters) {
      logger.info('Converting filter to query')
      queryFromFilters[`value.${key}`] = filterToQuery(filters[key]) as object
      logger.info('Got query ', queryFromFilters)
    }
  }
  const query = { ...queryFromFilters, typeName: readModelName }
  const result = await db.query(query)
  logger.debug('[ReadModelAdapter#searchReadModel] Search result: ', result)
  return result?.map((envelope) => envelope.value) ?? []
}

type QueryValue = number | string | boolean
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

/**
 * Transforms a GraphQL Booster filter into an neDB query
 */
function filterToQuery(filter: Filter<QueryValue>): QueryOperation<QueryValue> {
  const query = queryOperatorTable[filter.operation]
  return query(filter.values)
}
