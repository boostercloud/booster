/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  FilterFor,
  ReadModelEnvelope,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  SubscriptionEnvelope,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { ReadModelRegistry } from '../services/read-model-registry'

export const rawToEnvelopes = async (config: BoosterConfig, rawEvents: unknown): Promise<Array<ReadModelEnvelope>> =>
  rawEvents as Array<ReadModelEnvelope>

export const fetch = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  readModelID: UUID
): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>> => {
  const logger = getLogger(config, 'read-model-adapter#fetch')
  logger.debug('readModelAdapter#fetch: getting readModel')
  const value = (await registry.fetch(config, readModelName, readModelID)) as ReadModelInterface
  logger.debug('readModelAdapter#fetch: ' + JSON.stringify(value))
  return [value]
}

export const search = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  filters: FilterFor<unknown>
): Promise<Array<ReadModelInterface>> => {
  const logger = getLogger(config, 'read-model-adapter#search')
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  logger.debug('readModelAdapter#search: ' + readModelName + ' ' + JSON.stringify(filters))
  const result = await registry.search(config, readModelName, filters as unknown as any)
  logger.debug('readModelAdapter#search result ' + JSON.stringify(result))
  return result as Array<ReadModelInterface>
}

export const store = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> => {
  const logger = getLogger(config, 'read-model-adapter#store')
  logger.debug('[ReadModelAdapter#storeReadModel] Storing readModel ' + JSON.stringify(readModel))
  await registry.store(config, { typeName: readModelName, value: readModel })
  logger.debug('[ReadModelAdapter#storeReadModel] Read model stored')
}

export const deleteReadModel = async (
  registry: ReadModelRegistry,
  config: BoosterConfig,
  readModelName: string,
  readModel: ReadModelInterface
): Promise<void> => {
  throw new Error('readModelAdapter#deleteReadModel: Not implemented')
}

export const subscribe = async (config: BoosterConfig, subscriptionEnvelope: SubscriptionEnvelope): Promise<void> => {
  throw new Error('readModelAdapter#subscribe: Not implemented')
}

export const fetchSubscriptions = async (
  config: BoosterConfig,
  subscriptionID: string
): Promise<Array<SubscriptionEnvelope>> => {
  throw new Error('readModelAdapter#fetchSubscriptions: Not implemented')
}

export const deleteSubscription = async (
  config: BoosterConfig,
  connectionID: string,
  subscriptionID: string
): Promise<void> => {
  throw new Error('readModelAdapter#deleteSubscription: Not implemented')
}

export const deleteAllSubscriptions = async (config: BoosterConfig, connectionID: string): Promise<void> => {
  throw new Error('readModelAdapter#deleteAllSubscriptions: Not implemented')
}
