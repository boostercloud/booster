/* eslint-disable @typescript-eslint/no-explicit-any */
import {
  CommandEnvelope,
  EventEnvelope,
  GraphQLRequestEnvelope,
  ReadModelRequestEnvelope,
  SubscriptionEnvelope,
  UserEnvelope,
  ReadModelEnvelope,
} from './envelope'
import { BoosterConfig } from './config'
import { Observable } from 'rxjs'
import { Logger } from './logger'
import { ReadModelInterface, UUID } from './concepts'
import { Filter } from './searcher'
import { DynamoDBStreamEvent } from 'aws-lambda'

export type ProviderLibrary = ProviderCommandsLibrary &
  ProviderEventsLibrary &
  ProviderReadModelsLibrary &
  ProviderAuthLibrary &
  ProviderAPIHandling &
  ProviderInfrastructureGetter &
  ProviderGraphQLLibrary

export interface ProviderCommandsLibrary {
  rawCommandToEnvelope(rawCommand: any): Promise<CommandEnvelope>
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
  rawReadModelRequestToEnvelope(rawReadModelRequest: any): Promise<ReadModelRequestEnvelope>
  /** @deprecated */
  fetchReadModel(
    config: BoosterConfig,
    logger: Logger,
    readModelName: string,
    readModelID: UUID
  ): Promise<ReadModelInterface>
  /** @deprecated */
  fetchAllReadModels(config: BoosterConfig, logger: Logger, readModelName: string): Promise<Array<ReadModelInterface>>
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
    rawEvents: DynamoDBStreamEvent
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
  handleReadModelResult(readModels: ReadModelInterface | Array<ReadModelInterface>): Promise<any>
  handleReadModelError(error: Error): Promise<any>
}

export interface ProviderGraphQLLibrary {
  authorizeRequest(rawRequest: any, logger: Logger): Promise<any>
  rawGraphQLRequestToEnvelope(rawGraphQLRequest: any, logger: Logger): Promise<GraphQLRequestEnvelope>
  handleGraphQLResult(result?: any): Promise<any>
  handleGraphQLError(error: Error): Promise<any>
}

export interface ProviderAuthLibrary {
  rawSignUpDataToUserEnvelope(rawMessage: any): UserEnvelope
}

export interface ProviderAPIHandling {
  requestSucceeded(body?: any): Promise<any>
  requestFailed(error: Error): Promise<any>
}

export interface ProviderInfrastructureGetter {
  getInfrastructure(): ProviderInfrastructure
}

export interface ProviderInfrastructure {
  deploy?: (configuration: BoosterConfig) => Observable<string>
  run?: (configuration: BoosterConfig, port: number) => Promise<void>
  nuke(configuration: BoosterConfig): Observable<string>
}
