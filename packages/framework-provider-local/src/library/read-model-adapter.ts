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
  return rawEvents.map((event) => event as ReadModelEnvelope)
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
    console.log(`[ReadModelAdapter#fetchReadModel] Failed to fetch read model ${readModelName} with ID ${readModelID}`)
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
  filters: Record<string, Filter<any>>
): Promise<Array<any>> {
  logger.debug('FILTERS, FILTERS, GET THEM HOT: ', filters)
  const query = { typeName: readModelName }
  // let params: DocumentClient.ScanInput = {
  //   TableName: config.resourceNames.forReadModel(readModelName),
  //   ConsistentRead: true,
  // }
  // if (filters && Object.keys(filters).length > 0) {
  //   params = {
  //     ...params,
  //     FilterExpression: buildFilterExpression(filters),
  //     ExpressionAttributeNames: buildExpressionAttributeNames(filters),
  //     ExpressionAttributeValues: buildExpressionAttributeValues(filters),
  //   }
  // }
  // logger.debug('Running search with the following params: \n', params)

  const result = await db.query(query)

  logger.debug('Search result: ', result)

  return result.map((envelope) => envelope.value) ?? []
}
