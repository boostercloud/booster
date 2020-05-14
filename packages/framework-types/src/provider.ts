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
  rawToEnvelopes(rawEvents: any): Array<EventEnvelope>
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
  /** Streams an event to the corresponding event handler */
  storeAndPublish(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig, logger: Logger): Promise<void>
}
export interface ProviderReadModelsLibrary {
  fetch(config: BoosterConfig, logger: Logger, readModelName: string, readModelID: UUID): Promise<ReadModelInterface>
  search<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    filters: Record<string, Filter<any>>
  ): Promise<Array<TReadModel>>
  subscribe(config: BoosterConfig, logger: Logger, subscriptionEnvelope: SubscriptionEnvelope): Promise<void>
  rawToEnvelopes(config: BoosterConfig, logger: Logger, rawEvents: any): Promise<Array<ReadModelEnvelope>>
  fetchSubscriptions(
    config: BoosterConfig,
    logger: Logger,
    subscriptionName: string
  ): Promise<Array<SubscriptionEnvelope>>
  notifySubscription(config: BoosterConfig, connectionID: string, data: Record<string, any>): Promise<void>
  store(config: BoosterConfig, logger: Logger, readModelName: string, readModel: ReadModelInterface): Promise<any>
}

export interface ProviderGraphQLLibrary {
  authorizeRequest(rawRequest: any, logger: Logger): Promise<any>
  rawToEnvelope(rawGraphQLRequest: any, logger: Logger): Promise<GraphQLRequestEnvelope>
  handleResult(result?: any): Promise<any>
}

export interface ProviderAuthLibrary {
  rawToEnvelope(rawMessage: any): UserEnvelope
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
