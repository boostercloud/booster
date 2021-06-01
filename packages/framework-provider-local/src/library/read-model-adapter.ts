import {
  BoosterConfig,
  FilterFor,
  Logger,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelEnvelope,
  ReadModelInterface,
  UUID,
} from '@boostercloud/framework-types'
import { ReadModelRegistry } from '../services/read-model-registry'
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
): Promise<ReadModelInterface> {
  const response = await db.query({ typeName: readModelName, "value.id": readModelID })
  const item = response[0]
  if (!item) {
    logger.debug(`[ReadModelAdapter#fetchReadModel] Read model ${readModelName} with ID ${readModelID} not found`)
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
  readModel: ReadModelInterface,
  expectedCurrentVersion: number,
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
  filters: FilterFor<any>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<Array<any>> {
  logger.info('Converting filter to query')
  const query = queryRecordFor(readModelName, filters)
  logger.info('Got query ', query)
  const result = await db.query(query)
  logger.debug('[ReadModelAdapter#searchReadModel] Search result: ', result)
  return result?.map((envelope) => envelope.value) ?? []
}
