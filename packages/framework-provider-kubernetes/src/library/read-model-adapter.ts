/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  FilterFor,
  Logger,
  ReadModelEnvelope,
  ReadModelInterface,
  SubscriptionEnvelope,
  UUID,
} from '@boostercloud/framework-types'
import { ReadModelRegistry } from '../services/read-model-registry'

export const rawToEnvelopes = async (
  config: BoosterConfig,
  logger: Logger,
  rawEvents: unknown
): Promise<Array<ReadModelEnvelope>> => rawEvents as Array<ReadModelEnvelope>

export const fetch = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModelID: UUID
): Promise<ReadModelInterface> => {
  logger.debug('readModelAdapter#fetch: getting readModel')
  const value = (await registry.fetch(readModelName, readModelID, logger)) as ReadModelInterface
  logger.debug('readModelAdapter#fetch: ' + JSON.stringify(value))
  return value
}

export const search = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  filters: FilterFor<unknown>
): Promise<Array<ReadModelInterface>> => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  logger.debug('readModelAdapter#search: ' + readModelName + ' ' + JSON.stringify(filters))
  const result = await registry.search(config, logger, readModelName, (filters as unknown) as any)
  logger.debug('readModelAdapter#search result ' + JSON.stringify(result))
  return result as Array<ReadModelInterface>
}

export const store = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> => {
  logger.debug('[ReadModelAdapter#storeReadModel] Storing readModel ' + JSON.stringify(readModel))
  await registry.store({ typeName: readModelName, value: readModel }, logger)
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}

export const deleteReadModel = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  logger: Logger,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> => {
  throw new Error('readModelAdapter#deleteReadModel: Not implemented')
}

export const subscribe = async (
  config: BoosterConfig,
  logger: Logger,
  subscriptionEnvelope: SubscriptionEnvelope
): Promise<void> => {
  throw new Error('readModelAdapter#subscribe: Not implemented')
}

export const fetchSubscriptions = async (
  config: BoosterConfig,
  logger: Logger,
  subscriptionID: string
): Promise<Array<SubscriptionEnvelope>> => {
  throw new Error('readModelAdapter#fetchSubscriptions: Not implemented')
}

export const deleteSubscription = async (
  config: BoosterConfig,
  logger: Logger,
  connectionID: string,
  subscriptionID: string
): Promise<void> => {
  throw new Error('readModelAdapter#deleteSubscription: Not implemented')
}

export const deleteAllSubscriptions = async (
  config: BoosterConfig,
  logger: Logger,
  connectionID: string
): Promise<void> => {
  throw new Error('readModelAdapter#deleteAllSubscriptions: Not implemented')
}
