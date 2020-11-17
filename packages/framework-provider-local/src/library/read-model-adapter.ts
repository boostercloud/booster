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
  filters: Record<string, Filter<unknown>>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Promise<Array<any>> {
  if (Object.keys(filters).length != 0) {
    throw Error('Filters not supported yet by local provider, but got:' + JSON.stringify(filters))
  }
  const query = { typeName: readModelName }
  const result = await db.query(query)
  logger.debug('[ReadModelAdapter#searchReadModel] Search result: ', result)
  return result?.map((envelope) => envelope.value) ?? []
}
