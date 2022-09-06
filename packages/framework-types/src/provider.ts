import { EventInterface, ReadModelInterface, SequenceKey, UUID } from './concepts'
import { BoosterConfig } from './config'
import {
  ConnectionDataEnvelope,
  EventEnvelope,
  EventSearchParameters,
  EventSearchRequestArgs,
  EventSearchResponse,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  PaginatedEntitiesIdsResult,
  PaginatedEventSearchResponse,
  ReadModelEnvelope,
  ReadModelListResult,
  ScheduledCommandEnvelope,
  SubscriptionEnvelope,
} from './envelope'
import { FilterFor, SortFor } from './searcher'
import { ReadOnlyNonEmptyArray } from './typelevel'
import { RocketDescriptor } from './rockets'

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
    entityTypeName: string,
    entityID: UUID,
    since?: string
  ): Promise<Array<EventEnvelope>>
  latestEntitySnapshot(config: BoosterConfig, entityTypeName: string, entityID: UUID): Promise<EventEnvelope | null>
  search(
    config: BoosterConfig,
    parameters: EventSearchParameters,
    paginated: boolean
  ): Promise<Array<EventSearchResponse> | PaginatedEventSearchResponse>
  filteredSearch<TEvent extends EventInterface>(
    config: BoosterConfig,
    parameters: EventSearchRequestArgs<TEvent>
  ): Promise<PaginatedEventSearchResponse>
  searchEntitiesIDs(
    config: BoosterConfig,
    limit: number,
    afterCursor: Record<string, string> | undefined,
    entityTypeName: string
  ): Promise<PaginatedEntitiesIdsResult>
  /** Streams an event to the corresponding event handler */
  store(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig): Promise<void>
}
export interface ProviderReadModelsLibrary {
  rawToEnvelopes(config: BoosterConfig, rawEvents: unknown): Promise<Array<ReadModelEnvelope>>
  fetch(
    config: BoosterConfig,
    readModelName: string,
    readModelID: UUID,
    sequenceKey?: SequenceKey
  ): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>>
  search<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    entityTypeName: string,
    filters: FilterFor<unknown>,
    sortBy?: SortFor<unknown>,
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
    readModelName: string,
    readModel: ReadModelInterface,
    expectedCurrentVersion?: number
  ): Promise<unknown>
  delete(config: BoosterConfig, readModelName: string, readModel: ReadModelInterface | undefined): Promise<any>
  subscribe(config: BoosterConfig, subscriptionEnvelope: SubscriptionEnvelope): Promise<void>
  fetchSubscriptions(config: BoosterConfig, subscriptionName: string): Promise<Array<SubscriptionEnvelope>>
  deleteSubscription(config: BoosterConfig, connectionID: string, subscriptionID: string): Promise<void>
  deleteAllSubscriptions(config: BoosterConfig, connectionID: string): Promise<void>
}

export interface ProviderGraphQLLibrary {
  rawToEnvelope(
    config: BoosterConfig,
    rawGraphQLRequest: unknown
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
  deploy?: (config: BoosterConfig) => Promise<void>
  nuke?: (config: BoosterConfig) => Promise<void>
  start?: (config: BoosterConfig, port: number) => Promise<void>
  synth?: (config: BoosterConfig) => Promise<void>
}

export interface ScheduledCommandsLibrary {
  rawToEnvelope(config: BoosterConfig, rawMessage: unknown): Promise<ScheduledCommandEnvelope>
}

export interface HasInfrastructure {
  Infrastructure: (rockets?: RocketDescriptor[]) => ProviderInfrastructure
}
