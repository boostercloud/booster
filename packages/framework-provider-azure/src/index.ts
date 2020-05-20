/* eslint-disable @typescript-eslint/no-explicit-any */
import { ProviderInfrastructure, ProviderLibrary } from '@boostercloud/framework-types'
import {
  BoosterConfig,
  EventEnvelope,
  Filter,
  Logger,
  ReadModelInterface,
  SubscriptionEnvelope,
  UUID,
} from '@boostercloud/framework-types/dist'

export const Provider: ProviderLibrary = {
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: function rawEventsToEnvelopes(rawEvents: any): Array<EventEnvelope> {
      return []
    },
    store(config: BoosterConfig, logger: Logger, envelope: EventEnvelope): Promise<any> {
      return Promise.resolve()
    },
    forEntitySince(
      config: BoosterConfig,
      logger: Logger,
      entityTypeName: string,
      entityID: UUID,
      since?: string
    ): Promise<Array<EventEnvelope>> {
      return Promise.resolve([])
    },
    latestEntitySnapshot(
      config: BoosterConfig,
      logger: Logger,
      entityTypeName: string,
      entityID: UUID
    ): Promise<EventEnvelope | null> {
      return Promise.resolve(null)
    },
    /** Streams an event to the corresponding event handler */
    publish(eventEnvelopes: Array<EventEnvelope>, config: BoosterConfig, logger: Logger): Promise<void> {
      return Promise.resolve()
    },
  },
  // ProviderReadModelsLibrary
  readModels: {
    fetch(config: BoosterConfig, logger: Logger, readModelName: string, readModelID: UUID): Promise<any> {
      return Promise.resolve()
    },
    search<TReadModel extends ReadModelInterface>(
      config: BoosterConfig,
      logger: Logger,
      entityTypeName: string,
      filters: Record<string, Filter<any>>
    ): Promise<Array<any>> {
      return Promise.resolve([])
    },
    subscribe(config: BoosterConfig, logger: Logger, subscriptionEnvelope: SubscriptionEnvelope): Promise<void> {
      return Promise.resolve()
    },
    rawToEnvelopes(config: BoosterConfig, logger: Logger, rawEvents: any): Promise<Array<any>> {
      return Promise.resolve([])
    },
    fetchSubscriptions(config: BoosterConfig, logger: Logger, subscriptionName: string): Promise<Array<any>> {
      return Promise.resolve([])
    },
    notifySubscription(config: BoosterConfig, connectionID: string, data: Record<string, any>): Promise<void> {
      return Promise.resolve()
    },
    store(config: BoosterConfig, logger: Logger, readModelName: string, readModel: ReadModelInterface): Promise<any> {
      return Promise.resolve()
    },
  },
  // ProviderGraphQLLibrary
  graphQL: {
    authorizeRequest(rawRequest: any, logger: Logger): Promise<any> {
      return Promise.resolve()
    },
    rawToEnvelope(rawGraphQLRequest: any, logger: Logger): Promise<any> {
      return Promise.resolve()
    },
    handleResult(result?: any): Promise<any> {
      return Promise.resolve()
    },
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope(rawMessage: any): any {
      return null
    },
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded(body?: any): Promise<any> {
      return Promise.resolve()
    },
    requestFailed(error: Error): Promise<any> {
      return Promise.resolve()
    },
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}
