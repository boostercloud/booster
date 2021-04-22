/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
import { ProviderInfrastructure, ProviderLibrary, UserApp } from '@boostercloud/framework-types'
import { EventRegistry } from './services/event-registry'
import { ReadModelRegistry } from './services/read-model-registry'
import * as EventsAdapter from './library/events-adapter'
import * as ReadModelAdapter from './library/read-model-adapter'
import * as GraphQLAdapter from './library/graphql-adapter'
import * as AuthAdapter from './library/auth-adapter'
import * as ApiAdapter from './library/api-adapter'
import * as ScheduledAdapter from './library/scheduled-adapter'
import * as ConnectionsAdapter from './library/connections-adapter'
import * as path from 'path'
import { searchReadModel } from './library/searcher-adapter'
import { RedisAdapter } from './services/redis-adapter'

const storageUrl = 'http://localhost:3500'
const eventRegistry = new EventRegistry(storageUrl)
const readModelRegistry = new ReadModelRegistry(storageUrl)
const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
const redisDB = RedisAdapter.build()

export const Provider = (): ProviderLibrary => ({
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: EventsAdapter.rawToEnvelopes,
    store: EventsAdapter.store.bind(null, eventRegistry, userApp),
    forEntitySince: EventsAdapter.forEntitySince.bind(null, eventRegistry),
    latestEntitySnapshot: EventsAdapter.latestEntitySnapshot.bind(null, eventRegistry),
    search: EventsAdapter.search.bind(null, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    rawToEnvelopes: ReadModelAdapter.rawToEnvelopes,
    fetch: ReadModelAdapter.fetch.bind(null, readModelRegistry),
    // TODO: Remove generic constraint from ProviderReadModelsLibrary.search
    search: searchReadModel.bind(null, redisDB),
    store: ReadModelAdapter.store.bind(null, readModelRegistry),
    delete: ReadModelAdapter.deleteReadModel.bind(null, readModelRegistry),
    subscribe: ReadModelAdapter.subscribe,
    fetchSubscriptions: ReadModelAdapter.fetchSubscriptions,
    deleteSubscription: ReadModelAdapter.deleteSubscription,
    deleteAllSubscriptions: ReadModelAdapter.deleteAllSubscriptions,
  },
  // ProviderGraphQLLibrary
  graphQL: {
    rawToEnvelope: GraphQLAdapter.rawToEnvelope,
    handleResult: GraphQLAdapter.handleResult,
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: AuthAdapter.rawToEnvelope,
    fromAuthToken: AuthAdapter.fromAuthToken,
    handleSignUpResult: AuthAdapter.handleSignUpResult,
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded: ApiAdapter.requestSucceeded,
    requestFailed: ApiAdapter.requestFailed,
  },
  connections: {
    storeData: ConnectionsAdapter.storeData,
    fetchData: ConnectionsAdapter.fetchData,
    deleteData: ConnectionsAdapter.deleteData,
    sendMessage: ConnectionsAdapter.sendMessage,
  },
  // ScheduledCommandsLibrary
  scheduled: {
    rawToEnvelope: ScheduledAdapter.rawToEnvelope,
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
})
