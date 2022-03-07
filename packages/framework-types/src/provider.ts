import { ReadModelInterface, SequenceKey, UUID } from './concepts'
import { BoosterConfig } from './config'
import {
  ConnectionDataEnvelope,
  EventEnvelope,
  EventSearchParameters,
  EventSearchResponse,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  ReadModelEnvelope,
  ReadModelListResult,
  ScheduledCommandEnvelope,
  SubscriptionEnvelope,
} from './envelope'
import { Logger } from './logger'
import { FilterFor } from './searcher'
import { ReadOnlyNonEmptyArray } from './typelevel'
import { RocketDescriptor } from './rocket-descriptor'

export interface ProviderLibrary {
  events: ProviderEventsLibrary
  readModels: ProviderReadModelsLibrary
  graphQL: ProviderGraphQLLibrary
  api: ProviderAPIHandling
  connections: ProviderConnectionsLibrary
  scheduled: ScheduledCommandsLibrary
  infrastructure: () => ProviderInfrastructure
}

export interface ProviderEventsLibrary {
  rawToEnvelopes(rawEvents: unknown): Array<EventEnvelope>
  forEntitySince(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    entityID: UUID,
    since?: string
  ): Promise<Array<EventEnvelope>>
  latestEntitySnapshot(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    entityID: UUID
  ): Promise<EventEnvelope | null>
  search(config: BoosterConfig, logger: Logger, parameters: EventSearchParameters): Promise<Array<EventSearchResponse>>
  /** Streams an event to the corresponding event handler */
  store(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig, logger: Logger): Promise<void>
}
export interface ProviderReadModelsLibrary {
  rawToEnvelopes(config: BoosterConfig, logger: Logger, rawEvents: unknown): Promise<Array<ReadModelEnvelope>>
  fetch(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModelID: UUID,
    sequenceKey?: SequenceKey
  ): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>>
  search<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    filters: FilterFor<unknown>,
    limit?: number,
    afterCursor?: unknown,
    paginatedVersion?: boolean
  ): Promise<Array<TReadModel> | ReadModelListResult<TReadModel>>
  /**
   * If "expectedCurrentVersion" is provided, the underlying provider must throw the error OptimisticConcurrencyUnexpectedVersionError
   * if the current stored read model contains a version that's different from the provided one
   */
  store(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModel: ReadModelInterface,
    expectedCurrentVersion?: number
  ): Promise<unknown>
  delete(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModel: ReadModelInterface | undefined
  ): Promise<any>
  subscribe(config: BoosterConfig, logger: Logger, subscriptionEnvelope: SubscriptionEnvelope): Promise<void>
  fetchSubscriptions(
    config: BoosterConfig,
    logger: Logger,
    subscriptionName: string
  ): Promise<Array<SubscriptionEnvelope>>
  deleteSubscription(config: BoosterConfig, logger: Logger, connectionID: string, subscriptionID: string): Promise<void>
  deleteAllSubscriptions(config: BoosterConfig, logger: Logger, connectionID: string): Promise<void>
}

export interface ProviderGraphQLLibrary {
  rawToEnvelope(
    rawGraphQLRequest: unknown,
    logger: Logger,
    config: BoosterConfig
  ): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError>
  handleResult(result?: unknown, headers?: Record<string, string>): Promise<unknown>
}

export interface ProviderConnectionsLibrary {
  storeData(config: BoosterConfig, connectionID: string, data: ConnectionDataEnvelope): Promise<void>
  fetchData(config: BoosterConfig, connectionID: string): Promise<ConnectionDataEnvelope | undefined>
  deleteData(config: BoosterConfig, connectionID: string): Promise<void>
  sendMessage(config: BoosterConfig, connectionID: string, data: unknown): Promise<void>
}

export interface ProviderAPIHandling {
  requestSucceeded(body?: unknown): Promise<unknown>
  requestFailed(error: Error): Promise<unknown>
}

export interface ProviderInfrastructure {
  deploy?: (configuration: BoosterConfig, logger: Logger) => Promise<void>
  nuke?: (configuration: BoosterConfig, logger: Logger) => Promise<void>
  start?: (configuration: BoosterConfig, port: number) => Promise<void>
  synth?: (configuration: BoosterConfig, logger: Logger) => Promise<void>
}

export interface ScheduledCommandsLibrary {
  rawToEnvelope(rawMessage: unknown, logger: Logger): Promise<ScheduledCommandEnvelope>
}

export interface HasInfrastructure {
  Infrastructure: (rockets?: RocketDescriptor[]) => ProviderInfrastructure
}
