import {
  EventEnvelope,
  GraphQLRequestEnvelope,
  SubscriptionEnvelope,
  UserEnvelope,
  ReadModelEnvelope,
  ConnectionDataEnvelope,
  GraphQLRequestEnvelopeError,
  ScheduledCommandEnvelope,
  EventFilter,
  EventSearchResponse,
} from './envelope'
import { BoosterConfig } from './config'
import { Logger } from './logger'
import { ReadModelInterface, UUID } from './concepts'
import { FilterFor } from './searcher'

export interface ProviderLibrary {
  events: ProviderEventsLibrary
  readModels: ProviderReadModelsLibrary
  auth: ProviderAuthLibrary
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
  search(config: BoosterConfig, logger: Logger, filters: EventFilter): Promise<Array<EventSearchResponse>>
  /** Streams an event to the corresponding event handler */
  store(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig, logger: Logger): Promise<void>
}
export interface ProviderReadModelsLibrary {
  rawToEnvelopes(config: BoosterConfig, logger: Logger, rawEvents: unknown): Promise<Array<ReadModelEnvelope>>
  fetch(config: BoosterConfig, logger: Logger, readModelName: string, readModelID: UUID): Promise<ReadModelInterface>
  search<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    filters: FilterFor<unknown>
  ): Promise<Array<TReadModel>>
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
  delete(config: BoosterConfig, logger: Logger, readModelName: string, readModel: ReadModelInterface): Promise<any>
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

export interface ProviderAuthLibrary {
  rawToEnvelope(rawMessage: unknown): UserEnvelope
  fromAuthToken(token: string): Promise<UserEnvelope | undefined>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleSignUpResult(config: BoosterConfig, request: any, userEnvelope: UserEnvelope): any
}

export interface ProviderAPIHandling {
  requestSucceeded(body?: unknown): Promise<unknown>
  requestFailed(error: Error): Promise<unknown>
}

export interface ProviderInfrastructure {
  deploy?: (configuration: BoosterConfig, logger: Logger) => Promise<void>
  nuke?: (configuration: BoosterConfig, logger: Logger) => Promise<void>
  start?: (configuration: BoosterConfig, port: number) => Promise<void>
}

export interface ScheduledCommandsLibrary {
  rawToEnvelope(rawMessage: unknown, logger: Logger): Promise<ScheduledCommandEnvelope>
}
