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
  store(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig, logger: Logger): Promise<void>
  destroy(config: BoosterConfig, logger: Logger, entityTypeName: string, entityID: UUID): Promise<void>
}
export interface ProviderReadModelsLibrary {
  rawToEnvelopes(config: BoosterConfig, logger: Logger, rawEvents: any): Promise<Array<ReadModelEnvelope>>
  fetch(config: BoosterConfig, logger: Logger, readModelName: string, readModelID: UUID): Promise<ReadModelInterface>
  search<TReadModel extends ReadModelInterface>(
    config: BoosterConfig,
    logger: Logger,
    entityTypeName: string,
    filters: Record<string, Filter<any>>
  ): Promise<Array<TReadModel>>
  store(config: BoosterConfig, logger: Logger, readModelName: string, readModel: ReadModelInterface): Promise<any>
  subscribe(config: BoosterConfig, logger: Logger, subscriptionEnvelope: SubscriptionEnvelope): Promise<void>
  fetchSubscriptions(
    config: BoosterConfig,
    logger: Logger,
    subscriptionName: string
  ): Promise<Array<SubscriptionEnvelope>>
  notifySubscription(config: BoosterConfig, connectionID: string, data: Record<string, any>): Promise<void>
  deleteSubscription(config: BoosterConfig, logger: Logger, connectionID: string, subscriptionID: string): Promise<void>
  deleteAllSubscriptions(config: BoosterConfig, logger: Logger, connectionID: string): Promise<void>
}

export interface ProviderGraphQLLibrary {
  rawToEnvelope(rawGraphQLRequest: any, logger: Logger): Promise<GraphQLRequestEnvelope>
  handleResult(result?: any, headers?: Record<string, string>): Promise<any>
}

export interface ProviderAuthLibrary {
  rawToEnvelope(rawMessage: any): UserEnvelope
  handleSignUpResult(config: BoosterConfig, request: any, userEnvelope: UserEnvelope): any
}

export interface ProviderAPIHandling {
  requestSucceeded(body?: any): Promise<any>
  requestFailed(error: Error): Promise<any>
}

export interface ProviderInfrastructure {
  deploy?: (configuration: BoosterConfig) => Observable<string>
  nuke?: (configuration: BoosterConfig) => Observable<string>
  start?: (configuration: BoosterConfig, port: number) => Promise<void>
}
