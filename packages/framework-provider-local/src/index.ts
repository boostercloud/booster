import { HasInfrastructure, ProviderLibrary, RocketDescriptor, UserApp } from '@boostercloud/framework-types'
import {
  rawEventsToEnvelopes,
  readEntityEventsSince,
  readEntityLatestSnapshot,
  storeEvents,
  storeSnapshot,
} from './library/events-adapter'
import { requestSucceeded, requestFailed } from './library/api-adapter'
import { EventRegistry, GraphQLService, ReadModelRegistry } from './services'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'

import * as path from 'path'

import {
  deleteReadModel,
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  searchReadModel,
  storeReadModel,
} from './library/read-model-adapter'
import { searchEntitiesIds, searchEvents } from './library/events-search-adapter'
import { rawScheduledInputToEnvelope } from './library/scheduled-adapter'
import {
  deleteConnectionData,
  fetchConnectionData,
  sendMessageToConnection,
  storeConnectionData,
} from './library/connections-adapter'
import {
  deleteAllSubscriptions,
  deleteSubscription,
  fetchSubscriptions,
  subscribeToReadModel,
} from './library/subscription-adapter'
import { WebSocketRegistry } from './services/web-socket-registry'
import { connectionsDatabase, subscriptionDatabase } from './paths'
import { rawRocketInputToEnvelope } from './library/rocket-adapter'
import { WebSocketServerAdapter } from './library/web-socket-server-adapter'
import {
  areDatabaseReadModelsUp,
  databaseUrl,
  databaseEventsHealthDetails,
  graphqlFunctionUrl,
  isDatabaseEventUp,
  isGraphQLFunctionUp,
  rawRequestToSensorHealth,
  databaseReadModelsHealthDetails,
} from './library/health-adapter'

export * from './paths'
export * from './services'
import * as process from 'process'

const eventRegistry = new EventRegistry()
const readModelRegistry = new ReadModelRegistry()
const connectionRegistry = new WebSocketRegistry(connectionsDatabase)
const subscriptionRegistry = new WebSocketRegistry(subscriptionDatabase)
const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))
const graphQLService = new GraphQLService(userApp)

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependencies that are deployed in the lambda functions. The infrastructure
 * package is only used during the deployment.
 */
export function loadInfrastructurePackage(packageName: string): HasInfrastructure {
  return require(packageName)
}

export const Provider = (rocketDescriptors?: RocketDescriptor[]): ProviderLibrary => ({
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    forEntitySince: readEntityEventsSince.bind(null, eventRegistry),
    latestEntitySnapshot: readEntityLatestSnapshot.bind(null, eventRegistry),
    store: storeEvents.bind(null, userApp, eventRegistry),
    storeSnapshot: storeSnapshot.bind(null, eventRegistry),
    search: searchEvents.bind(null, eventRegistry),
    searchEntitiesIDs: searchEntitiesIds.bind(null, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    rawToEnvelopes: rawReadModelEventsToEnvelopes,
    fetch: fetchReadModel.bind(null, readModelRegistry),
    search: searchReadModel.bind(null, readModelRegistry),
    store: storeReadModel.bind(null, graphQLService, readModelRegistry),
    delete: deleteReadModel.bind(null, readModelRegistry),
    subscribe: subscribeToReadModel.bind(null, subscriptionRegistry),
    fetchSubscriptions: fetchSubscriptions.bind(null, subscriptionRegistry),
    deleteSubscription: deleteSubscription.bind(null, subscriptionRegistry),
    deleteAllSubscriptions: deleteAllSubscriptions.bind(null, subscriptionRegistry),
  },
  // ProviderGraphQLLibrary
  graphQL: {
    rawToEnvelope: rawGraphQLRequestToEnvelope,
    handleResult: requestSucceeded,
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded,
    requestFailed,
  },
  connections: {
    storeData: storeConnectionData.bind(null, connectionRegistry),
    fetchData: fetchConnectionData.bind(null, connectionRegistry),
    deleteData: deleteConnectionData.bind(null, connectionRegistry),
    sendMessage: sendMessageToConnection.bind(null, new WebSocketServerAdapter(graphQLService, userApp.Booster.config)),
  },
  // ScheduledCommandsLibrary
  scheduled: {
    rawToEnvelope: rawScheduledInputToEnvelope,
  },
  rockets: {
    rawToEnvelopes: rawRocketInputToEnvelope,
  },
  sensor: {
    databaseEventsHealthDetails: databaseEventsHealthDetails.bind(null, eventRegistry),
    databaseReadModelsHealthDetails: databaseReadModelsHealthDetails.bind(null, readModelRegistry),
    isDatabaseEventUp: isDatabaseEventUp,
    areDatabaseReadModelsUp: areDatabaseReadModelsUp,
    databaseUrls: databaseUrl,
    isGraphQLFunctionUp: isGraphQLFunctionUp,
    graphQLFunctionUrl: graphqlFunctionUrl,
    rawRequestToHealthEnvelope: rawRequestToSensorHealth,
  },
  // ProviderInfrastructureGetter
  infrastructure: () => {
    const infrastructurePackageName = require('../package.json').name + '-infrastructure'
    let infrastructure: HasInfrastructure | undefined

    try {
      infrastructure = loadInfrastructurePackage(infrastructurePackageName)
    } catch (e) {
      throw new Error(
        `The Local infrastructure package could not be loaded. The following error was thrown: ${e.message}. Please ensure that one of the following actions has been done:\n` +
          `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
          `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
      )
    }

    return infrastructure.Infrastructure(rocketDescriptors)
  },
})
