import { ReadModelInterface, SequenceKey, UUID } from './concepts'
import { BoosterConfig } from './config'
import {
  ConnectionDataEnvelope,
  EntitySnapshotEnvelope,
  NonPersistedEntitySnapshotEnvelope,
  EventEnvelope,
  NonPersistedEventEnvelope,
  EventSearchParameters,
  EventSearchResponse,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  PaginatedEntitiesIdsResult,
  ReadModelEnvelope,
  ReadModelListResult,
  ScheduledCommandEnvelope,
  SubscriptionEnvelope,
  HealthEnvelope,
} from './envelope'
import { FilterFor, SortFor } from './searcher'
import { ReadOnlyNonEmptyArray } from './typelevel'
import { RocketDescriptor, RocketEnvelope } from './rockets'

export interface ProviderLibrary {
  events: ProviderEventsLibrary
  readModels: ProviderReadModelsLibrary
  graphQL: ProviderGraphQLLibrary
  api: ProviderAPIHandling
  connections: ProviderConnectionsLibrary
  scheduled: ScheduledCommandsLibrary
  infrastructure: () => ProviderInfrastructure
  rockets: ProviderRocketLibrary
  sensor: ProviderSensorLibrary
}

export interface ProviderRocketLibrary {
  rawToEnvelopes(config: BoosterConfig, request: unknown): RocketEnvelope
}

export interface ProviderSensorLibrary {
  databaseEventsHealthDetails(config: BoosterConfig): Promise<unknown>
  databaseReadModelsHealthDetails(config: BoosterConfig): Promise<unknown>
  isDatabaseEventUp(config: BoosterConfig): Promise<boolean>
  areDatabaseReadModelsUp(config: BoosterConfig): Promise<boolean>
  databaseUrls(config: BoosterConfig): Promise<Array<string>>
  isGraphQLFunctionUp(config: BoosterConfig): Promise<boolean>
  graphQLFunctionUrl(config: BoosterConfig): Promise<string>
  rawRequestToHealthEnvelope(rawRequest: unknown): HealthEnvelope
}

export interface ProviderEventsLibrary {
  /**
   * Converts raw events data into an array of EventEnvelope objects
   *
   * @param rawEvents - The raw events data to be converted
   * @returns An array of EventEnvelope objects
   */
  rawToEnvelopes(rawEvents: unknown): Array<EventEnvelope>

  /**
   * Retrieves events for a specific entity since a given time
   *
   * @param config - The Booster configuration object
   * @param entityTypeName - The type name of the entity
   * @param entityID - The ID of the entity
   * @param since - The time to retrieve events since (optional)
   * @returns A promise that resolves to an array of EventEnvelope objects
   */
  forEntitySince(
    config: BoosterConfig,
    entityTypeName: string,
    entityID: UUID,
    since?: string
  ): Promise<Array<EventEnvelope>>

  /**
   * Retrieves the latest snapshot of an entity
   *
   * @param config - The Booster configuration object
   * @param entityTypeName - The type name of the entity
   * @param entityID - The ID of the entity
   * @returns A promise that resolves to the latest EventEnvelope for the entity, or null if none exist
   */
  latestEntitySnapshot(
    config: BoosterConfig,
    entityTypeName: string,
    entityID: UUID
  ): Promise<EntitySnapshotEnvelope | undefined>

  /**
   * Searches for events based on specific parameters
   *
   * @param config - The Booster configuration object
   * @param parameters - The search parameters
   * @returns A promise that resolves to an array of EventSearchResponse objects
   */
  search(config: BoosterConfig, parameters: EventSearchParameters): Promise<Array<EventSearchResponse>>

  /**
   * Searches for entities IDs based on a specific entity type and pagination parameters
   *
   * @param config - The Booster configuration object
   * @param limit - The maximum number of entities IDs to retrieve
   * @param afterCursor - The cursor to retrieve entities IDs after (optional)
   * @param entityTypeName - The type name of the entities to search for
   * @returns A promise that resolves to a PaginatedEntitiesIdsResult object
   */
  searchEntitiesIDs(
    config: BoosterConfig,
    limit: number,
    afterCursor: Record<string, string> | undefined,
    entityTypeName: string
  ): Promise<PaginatedEntitiesIdsResult>

  /**
   * Streams an event to the corresponding event handler
   *
   * @param eventEnvelopes - The array of `NonPersistedEventEnvelope` objects to store
   * @param config - The Booster configuration object
   * @returns A promise that resolves with the list of `EventEnvelope`s when the events have been stored
   */
  store(eventEnvelopes: Array<NonPersistedEventEnvelope>, config: BoosterConfig): Promise<Array<EventEnvelope>>

  /**
   * Stores a snapshot of an entity
   *
   * @param snapshotEnvelope - The `NonPersistedEntitySnapshotEnvelope` object to store
   * @param config - The Booster configuration object
   * @returns A promise that resolves with the `EntitySnapshotEnvelope` when the snapshot has been stored
   */
  storeSnapshot(
    snapshotEnvelope: NonPersistedEntitySnapshotEnvelope,
    config: BoosterConfig
  ): Promise<EntitySnapshotEnvelope>
}

export interface ProviderReadModelsLibrary {
  /**
   * Converts raw events into `ReadModelEnvelope` objects.
   *
   * @param config - The Booster configuration object.
   * @param rawEvents - The raw events to be converted.
   * @returns A promise that resolves to an array of `ReadModelEnvelope` objects.
   */
  rawToEnvelopes(config: BoosterConfig, rawEvents: unknown): Promise<Array<ReadModelEnvelope>>

  /**
   * Fetches a read model by name and ID.
   *
   * @param config - The Booster configuration object.
   * @param readModelName - The name of the read model to be fetched.
   * @param readModelID - The ID of the read model to be fetched.
   * @param sequenceKey - The sequence key of the read model to be fetched (optional).
   * @returns A promise that resolves to a read-only non-empty array of `ReadModelInterface` objects.
   */
  fetch(
    config: BoosterConfig,
    readModelName: string,
    readModelID: UUID,
    sequenceKey?: SequenceKey
  ): Promise<ReadOnlyNonEmptyArray<ReadModelInterface>>

  /**
   * Searches for read models that match a set of filters.
   *
   * @template TReadModel - The type of read model to be returned.
   * @param config - The Booster configuration object.
   * @param entityTypeName - The name of the entity type to be searched.
   * @param filters - The filters to be applied during the search.
   * @param sortBy - An object that specifies how the results should be sorted (optional).
   * @param limit - The maximum number of results to return (optional).
   * @param afterCursor - A cursor that specifies the position after which results should be returned (optional).
   * @param paginatedVersion - A boolean value that indicates whether the results should be paginated (optional).
   * @returns A promise that resolves to an array of `TReadModel` objects or a `ReadModelListResult` object.
   */
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
   * Stores a read model.
   *
   * @param config - The Booster configuration object.
   * @param readModelName - The name of the read model to be stored.
   * @param readModel - The read model to be stored.
   * @param expectedCurrentVersion - The expected current version of the read model (optional).
   * If is provided, the underlying provider must throw the error OptimisticConcurrencyUnexpectedVersionError
   * if the current stored read model contains a version that's different from the provided one.
   *
   * @returns A promise that resolves to an unknown value.
   */
  store(
    config: BoosterConfig,
    readModelName: string,
    readModel: ReadModelInterface,
    expectedCurrentVersion?: number
  ): Promise<unknown>

  /**
   * Deletes a read model.
   *
   * @param config - The Booster configuration object.
   * @param readModelName - The name of the read model to be deleted.
   * @param readModel - The read model to be deleted (optional).
   * @returns A promise that resolves to any value.
   */
  delete(config: BoosterConfig, readModelName: string, readModel: ReadModelInterface | undefined): Promise<unknown>

  /**
   * Subscribes to a stream of events.
   *
   * @param config - The Booster configuration object.
   * @param subscriptionEnvelope - The subscription envelope that contains the details of the subscription.
   * @returns A promise that resolves to void.
   */
  subscribe(config: BoosterConfig, subscriptionEnvelope: SubscriptionEnvelope): Promise<void>

  /**
   * Fetches a list of subscriptions by subscription name.
   *
   * @param config - The Booster configuration object.
   * @param subscriptionName - The name of the subscriptions to be fetched.
   * @returns A promise that resolves to an array of `SubscriptionEnvelope` objects.
   */
  fetchSubscriptions(config: BoosterConfig, subscriptionName: string): Promise<Array<SubscriptionEnvelope>>

  /**
   * Deletes a subscription by connection ID and subscription ID.
   *
   * @param config - The Booster configuration object.
   * @param connectionID - The ID of the connection associated with the subscription.
   * @param subscriptionID - The ID of the subscription to be deleted.
   * @returns A promise that resolves to void.
   */
  deleteSubscription(config: BoosterConfig, connectionID: string, subscriptionID: string): Promise<void>

  /**
   * Deletes all subscriptions for a connection by connection ID.
   *
   * @param config - The Booster configuration object.
   * @param connectionID - The ID of the connection associated with the subscriptions.
   * @returns A promise that resolves to void.
   */
  deleteAllSubscriptions(config: BoosterConfig, connectionID: string): Promise<void>
}

export interface ProviderGraphQLLibrary {
  /**
   * Converts a raw GraphQL request to a `GraphQLRequestEnvelope` or a `GraphQLRequestEnvelopeError`.
   *
   * @param config - The Booster configuration object.
   * @param rawGraphQLRequest - The raw GraphQL request to be converted.
   * @returns A promise that resolves to either a `GraphQLRequestEnvelope` or a `GraphQLRequestEnvelopeError` object.
   */
  rawToEnvelope(
    config: BoosterConfig,
    rawGraphQLRequest: unknown
  ): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError>

  /**
   * Handles the result of a GraphQL request.
   *
   * @param result - The result of the GraphQL request (optional).
   * @param headers - The headers associated with the GraphQL request result (optional).
   * @returns A promise that resolves to any value.
   */
  handleResult(result?: unknown, headers?: Record<string, string>): Promise<unknown>
}

export interface ProviderConnectionsLibrary {
  /**
   * Stores connection data for a specific connection.
   *
   * @param config - The Booster configuration object.
   * @param connectionID - The ID of the connection.
   * @param data - The data to be stored for the connection.
   * @returns A promise that resolves when the data has been stored successfully.
   */
  storeData(config: BoosterConfig, connectionID: string, data: ConnectionDataEnvelope): Promise<void>

  /**
   * Fetches connection data for a specific connection.
   *
   * @param config - The Booster configuration object.
   * @param connectionID - The ID of the connection.
   * @returns A promise that resolves to the connection data for the specified connection, or `undefined` if no data is found.
   */
  fetchData(config: BoosterConfig, connectionID: string): Promise<ConnectionDataEnvelope | undefined>

  /**
   * Deletes connection data for a specific connection.
   *
   * @param config - The Booster configuration object.
   * @param connectionID - The ID of the connection.
   * @returns A promise that resolves when the data has been deleted successfully.
   */
  deleteData(config: BoosterConfig, connectionID: string): Promise<void>

  /**
   * Sends a message to a specific connection.
   *
   * @param config - The Booster configuration object.
   * @param connectionID - The ID of the connection.
   * @param data - The data to be sent to the connection.
   * @returns A promise that resolves when the message has been sent successfully.
   */
  sendMessage(config: BoosterConfig, connectionID: string, data: unknown): Promise<void>
}

export interface ProviderAPIHandling {
  /**
   * Handles a successful API request by returning the response body.
   *
   * @param body - The response body of the API request.
   * @param headers - The headers of the API request.
   * @returns A promise that resolves with the response body.
   */
  requestSucceeded(body?: unknown, headers?: Record<string, number | string | ReadonlyArray<string>>): Promise<unknown>

  /**
   * Handles a failed API request by returning an error.
   *
   * @param error - The error that occurred during the API request.
   * @returns A promise that resolves with the error.
   */
  requestFailed(error: Error): Promise<unknown>
}

export interface ProviderInfrastructure {
  /**
   * Deploys the application.
   *
   * @param config - The configuration for the application.
   * @returns A promise that resolves when the deployment is complete.
   */
  deploy?: (config: BoosterConfig) => Promise<void>

  /**
   * Deletes all resources created by the application.
   *
   * @param config - The configuration for the application.
   * @returns A promise that resolves when the deletion is complete.
   */
  nuke?: (config: BoosterConfig) => Promise<void>

  /**
   * Starts the application.
   *
   * @param config - The configuration for the application.
   * @param port - The port number to start the application on.
   * @returns A promise that resolves when the application has started.
   */
  start?: (config: BoosterConfig, port: number) => Promise<void>

  /**
   * Synthesizes the application.
   *
   * @param config - The configuration for the application.
   * @returns A promise that resolves when the synthesis is complete.
   */
  synth?: (config: BoosterConfig) => Promise<void>
}

export interface ScheduledCommandsLibrary {
  /**
   * Converts a raw message into a `ScheduledCommandEnvelope`.
   *
   * @param config - The configuration for the application.
   * @param rawMessage - The raw message to convert.
   * @returns A promise that resolves with the `ScheduledCommandEnvelope` representation of the raw message.
   */
  rawToEnvelope(config: BoosterConfig, rawMessage: unknown): Promise<ScheduledCommandEnvelope>
}

export interface HasInfrastructure {
  /**
   * Creates a `ProviderInfrastructure` instance.
   *
   * @param rockets - An optional array of `RocketDescriptor` objects.
   * @returns A `ProviderInfrastructure` instance.
   */
  Infrastructure: (rockets?: RocketDescriptor[]) => ProviderInfrastructure
}
