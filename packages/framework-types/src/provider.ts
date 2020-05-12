/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  EventEnvelope,
  GraphQLRequestEnvelope,
  SubscriptionEnvelope,
  UserEnvelope,
  ReadModelEnvelope,
} from './envelope'
import { BoosterConfig } from './config'
import { Observable } from 'rxjs'
import { Logger } from './logger'
import { ReadModelInterface, UUID } from './concepts'
import { Filter } from './searcher'

export interface ProviderLibrary {
  events: ProviderEventsLibrary
  readModels: ProviderReadModelsLibrary
  auth: ProviderAuthLibrary
  api: ProviderAPIHandling
  infrastructure: () => ProviderInfrastructure
  graphQL: ProviderGraphQLLibrary
}

export interface ProviderEventsLibrary {
  rawEventsToEnvelopes(rawEvents: any): Array<EventEnvelope>
  /** Stores an event in the event store */
  storeEvent(config: BoosterConfig, logger: Logger, envelope: EventEnvelope): Promise<any>
  readEntityEventsSince(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    entityID: UUID,
    since?: string
  ): Promise<Array<EventEnvelope>>
  readEntityLatestSnapshot(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    entityID: UUID
  ): Promise<EventEnvelope | null>
  /** Streams an event to the corresponding event handler */
  publishEvents(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig, logger: Logger): Promise<void>
}
export interface ProviderReadModelsLibrary {
  fetchReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModelID: UUID
  ): Promise<ReadModelInterface>
  searchReadModel<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    filters: Record<string, Filter<any>>
  ): Promise<Array<TReadModel>>
  subscribeToReadModel(config: BoosterConfig, logger: Logger, subscriptionEnvelope: SubscriptionEnvelope): Promise<void>
  rawReadModelEventsToEnvelopes(
    config: BoosterConfig,
    logger: Logger,
    rawEvents: any
  ): Promise<Array<ReadModelEnvelope>>
  fetchSubscriptions(
    config: BoosterConfig,
    logger: Logger,
    subscriptionName: string
  ): Promise<Array<SubscriptionEnvelope>>
  notifySubscription(config: BoosterConfig, connectionID: string, data: Record<string, any>): Promise<void>
  storeReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModel: ReadModelInterface
  ): Promise<any>
}

export interface ProviderGraphQLLibrary {
  authorizeRequest(rawRequest: any, logger: Logger): Promise<any>
  rawGraphQLRequestToEnvelope(rawGraphQLRequest: any, logger: Logger): Promise<GraphQLRequestEnvelope>
  handleGraphQLResult(result?: any): Promise<any>
}

export interface ProviderAuthLibrary {
  rawSignUpDataToUserEnvelope(rawMessage: any): UserEnvelope
}

export interface ProviderAPIHandling {
  requestSucceeded(body?: any): Promise<any>
  requestFailed(error: Error): Promise<any>
}

export interface ProviderInfrastructure {
  deploy?: (configuration: BoosterConfig) => Observable<string>
  run?: (configuration: BoosterConfig, port: number) => Promise<void>
  nuke(configuration: BoosterConfig): Observable<string>
}
