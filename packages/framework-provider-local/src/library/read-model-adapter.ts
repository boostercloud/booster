import {
  BoosterConfig,
  FilterFor,
  Logger,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadModelListResult,
  ReadOnlyNonEmptyArray,
  SortFor,
  UUID,
} from '@boostercloud/framework-types'
import { ReadModelRegistry } from '../services'
import { queryRecordFor } from './searcher-adapter'

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
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> {
  //use dot notation value.id to match the record (see https://github.com/louischatriot/nedb#finding-documents)
  const response = await db.query({ typeName: readModelName, 'value.id': readModelID })
  const item = response[0]
  if (!item) {
    logger.debug(`[ReadModelAdapter#fetchReadModel] Read model ${readModelName} with ID ${readModelID} not found`)
  } else {
    logger.debug(
      `[ReadModelAdapter#fetchReadModel] Loaded read model ${readModelName} with ID ${readModelID} with result:`,
      item.value
    )
  }
  return [item?.value]
}

export async function storeReadModel(
  db: ReadModelRegistry,
  _config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface,
  expectedCurrentVersion: number
): Promise<void> {
  logger.debug('[ReadModelAdapter#storeReadModel] Storing readModel ' + JSON.stringify(readModel))
  try {
    await db.store({ typeName: readModelName, value: readModel } as ReadModelEnvelope)
  } catch (e) {
    // The error will be thrown, but in case of a conditional check, we throw the expected error type by the core
    // TODO: verify the name of the exception thrown in Local Provider
    if (e.name == 'TODO') {
      throw new OptimisticConcurrencyUnexpectedVersionError(e.message)
    }
    throw e
  }
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}

export async function searchReadModel(
  db: ReadModelRegistry,
  _config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>,
  sortBy?: SortFor<unknown>,
  limit?: number,
  afterCursor?: Record<string, string> | undefined,
  paginatedVersion = false
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<Array<any> | ReadModelListResult<any>> {
  logger.debug('Converting filter to query')
  const queryFor = queryRecordFor(filters)
  const query = { ...queryFor, typeName: readModelName }
  logger.debug('Got query ', query)
  const skipId = afterCursor?.id ? parseInt(afterCursor?.id) : 0
  const result = await db.query(query, sortBy, skipId, limit)
  logger.debug('[ReadModelAdapter#searchReadModel] Search result: ', result)
  const items = result?.map((envelope) => envelope.value) ?? []
  if (paginatedVersion) {
    return {
      items: items,
      count: items?.length ?? 0,
      cursor: { id: ((limit ? limit : 1) + skipId).toString() },
    }
  }
  return items
}
