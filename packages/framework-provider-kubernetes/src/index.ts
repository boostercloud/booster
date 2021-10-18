/* eslint-disable @typescript-eslint/no-unused-vars */
/* eslint-disable @typescript-eslint/no-explicit-any */
import { HasInfrastructure, ProviderLibrary, UserApp } from '@boostercloud/framework-types'
import { EventRegistry } from './services/event-registry'
import { ReadModelRegistry } from './services/read-model-registry'
import * as EventsAdapter from './library/events-adapter'
import * as ReadModelAdapter from './library/read-model-adapter'
import * as GraphQLAdapter from './library/graphql-adapter'
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

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependencies that are deployed in the lambda functions. The infrastructure
 * package is only used during the deploy.
 */
export function loadInfrastructurePackage(packageName: string): HasInfrastructure {
  return require(packageName)
}

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
  infrastructure: () => {
    const infrastructurePackageName = require('../package.json').name + '-infrastructure'
    let infrastructure: HasInfrastructure | undefined

    try {
      infrastructure = loadInfrastructurePackage(infrastructurePackageName)
    } catch (e) {
      throw new Error(
        `The Kubernetes infrastructure package could not be loaded. The following error was thrown: ${e.message}. Please ensure that one of the following actions has been done:\n` +
          `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
          `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
      )
    }

    return infrastructure.Infrastructure()
  },
})
