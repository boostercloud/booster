/* eslint-disable @typescript-eslint/no-explicit-any */
import { HasInfrastructure, ProviderLibrary, RocketDescriptor } from '@boostercloud/framework-types'
import { healthRequestResult, requestFailed, requestSucceeded } from './library/api-adapter'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import {
  rawEventsToEnvelopes,
  readEntityEventsSince,
  readEntityLatestSnapshot,
  storeDispatchedEvent,
  storeSnapshot,
} from './library/events-adapter'
import { CosmosClient, CosmosClientOptions } from '@azure/cosmos'
import { environmentVarNames } from './constants'
import {
  deleteReadModel,
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  storeReadModel,
} from './library/read-model-adapter'
import { searchReadModel } from './library/searcher-adapter'
import { rawScheduledInputToEnvelope } from './library/scheduled-adapter'
import { searchEntitiesIds, searchEvents } from './library/events-searcher-adapter'
import {
  deleteAllSubscriptions,
  deleteSubscription,
  fetchSubscriptions,
  subscribeToReadModel,
} from './library/subscription-adapter'
import {
  deleteConnectionData,
  fetchConnectionData,
  sendMessageToConnection,
  storeConnectionData,
} from './library/connections-adapter'
import { rawRocketInputToEnvelope } from './library/rocket-adapter'
import { produceEventsStream } from './library/events-stream-producer-adapter'
import { EventHubProducerClient, RetryMode } from '@azure/event-hubs'
import { dedupEventStream, rawEventsStreamToEnvelopes } from './library/events-stream-consumer-adapter'
import {
  areDatabaseReadModelsUp,
  areRocketFunctionsUp,
  databaseEventsHealthDetails,
  databaseReadModelsHealthDetails,
  databaseUrl,
  graphqlFunctionUrl,
  isDatabaseEventUp,
  isGraphQLFunctionUp,
  rawRequestToSensorHealth,
} from './library/health-adapter'
import { deleteEvent, deleteSnapshot, findDeletableEvent, findDeletableSnapshot } from './library/event-delete-adapter'
import { storeEvents } from './library/events-store-adapter'
import { ConfigurationAdapter } from './library/configuration-adapter'

let cosmosClient: CosmosClient
if (typeof process.env[environmentVarNames.cosmosDbConnectionString] === 'undefined') {
  cosmosClient = {} as any
} else {
  const cosmosClientOptions: CosmosClientOptions = {
    connectionString: process.env[environmentVarNames.cosmosDbConnectionString] as string,
    // Overrides default retry options if any of the environment variables are set
    ...((process.env[environmentVarNames.cosmosDbMaxRetries] ||
      process.env[environmentVarNames.cosmosDbRetryInterval] ||
      process.env[environmentVarNames.cosmosDbMaxWaitTime]) && {
      connectionPolicy: {
        retryOptions: {
          ...(process.env[environmentVarNames.cosmosDbMaxRetries] && {
            maxRetryAttemptCount: Number(process.env[environmentVarNames.cosmosDbMaxRetries]),
          }),
          ...(process.env[environmentVarNames.cosmosDbRetryInterval] && {
            fixedRetryIntervalInMilliseconds: Number(process.env[environmentVarNames.cosmosDbRetryInterval]),
          }),
          ...(process.env[environmentVarNames.cosmosDbMaxWaitTime] && {
            maxWaitTimeInSeconds: Number(process.env[environmentVarNames.cosmosDbMaxWaitTime]),
          }),
        },
      },
    }),
  }
  cosmosClient = new CosmosClient(cosmosClientOptions)
}

let producer: EventHubProducerClient
const eventHubConnectionString = process.env[environmentVarNames.eventHubConnectionString]
const eventHubName = process.env[environmentVarNames.eventHubName]
const DEFAULT_MAX_RETRY = 5
const DEFAULT_EVENT_HUB_MODE = RetryMode.Exponential
if (
  typeof eventHubConnectionString === 'undefined' ||
  typeof eventHubName === 'undefined' ||
  eventHubConnectionString === '' ||
  eventHubName === ''
) {
  producer = {} as any
} else {
  const maxRetries = process.env[environmentVarNames.eventHubMaxRetries]
    ? Number(process.env[environmentVarNames.eventHubMaxRetries])
    : DEFAULT_MAX_RETRY
  const mode =
    process.env[environmentVarNames.eventHubMaxRetries] &&
    process.env[environmentVarNames.eventHubMode]?.toUpperCase() === 'FIXED'
      ? RetryMode.Fixed
      : DEFAULT_EVENT_HUB_MODE
  producer = new EventHubProducerClient(eventHubConnectionString, eventHubName, {
    retryOptions: {
      maxRetries: maxRetries,
      mode: mode,
    },
  })
}

const azureAppConfigConnectionString = process.env[environmentVarNames.appConfigurationConnectionString]
const azureAppConfigEndpoint = process.env[environmentVarNames.appConfigurationEndpoint]

if (azureAppConfigConnectionString || azureAppConfigEndpoint) {
  try {
    const config = require('@boostercloud/framework-core').Booster.config

    const azureAppConfigOptions = (config as any)._azureAppConfigOptions

    // Use user overrides if provided, otherwise fall back to environment variables
    const connectionString = azureAppConfigOptions?.connectionString || azureAppConfigConnectionString
    const endpoint = azureAppConfigOptions?.endpoint || azureAppConfigEndpoint
    const labelFilter = azureAppConfigOptions?.labelFilter

    // Initialize if we have either environment variables or user config with enabled=true
    if (connectionString || endpoint || azureAppConfigOptions?.enabled) {
      const provider = connectionString
        ? ConfigurationAdapter.withConnectionString(connectionString, labelFilter)
        : ConfigurationAdapter.withEndpoint(endpoint, labelFilter)
      config.addConfigurationProvider(provider)
    }
  } catch (error) {
    console.warn('[Azure Provider] Failed to initialize Azure App Configuration adapter:', error)
  }
}

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependencies that are deployed in the lambda functions. The infrastructure
 * package is only used during the deploy.
 */
export function loadInfrastructurePackage(packageName: string): HasInfrastructure {
  return require(packageName)
}

export const Provider = (rockets?: RocketDescriptor[]): ProviderLibrary => ({
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    rawStreamToEnvelopes: rawEventsStreamToEnvelopes,
    dedupEventStream: dedupEventStream.bind(null, cosmosClient),
    produce: produceEventsStream.bind(null, producer),
    store: storeEvents.bind(null, cosmosClient),
    storeSnapshot: storeSnapshot.bind(null, cosmosClient),
    forEntitySince: readEntityEventsSince.bind(null, cosmosClient),
    latestEntitySnapshot: readEntityLatestSnapshot.bind(null, cosmosClient),
    search: searchEvents.bind(null, cosmosClient),
    searchEntitiesIDs: searchEntitiesIds.bind(null, cosmosClient),
    storeDispatched: storeDispatchedEvent.bind(null, cosmosClient),
    findDeletableEvent: findDeletableEvent.bind(null, cosmosClient),
    findDeletableSnapshot: findDeletableSnapshot.bind(null, cosmosClient),
    deleteEvent: deleteEvent.bind(null, cosmosClient),
    deleteSnapshot: deleteSnapshot.bind(null, cosmosClient),
  },
  // ProviderReadModelsLibrary
  readModels: {
    fetch: fetchReadModel.bind(null, cosmosClient),
    search: searchReadModel.bind(null, cosmosClient),
    rawToEnvelopes: rawReadModelEventsToEnvelopes,
    store: storeReadModel.bind(null, cosmosClient),
    delete: deleteReadModel.bind(null, cosmosClient),
    subscribe: subscribeToReadModel.bind(null, cosmosClient),
    fetchSubscriptions: fetchSubscriptions.bind(null, cosmosClient),
    deleteSubscription: deleteSubscription.bind(null, cosmosClient),
    deleteAllSubscriptions: deleteAllSubscriptions.bind(null, cosmosClient),
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
    healthRequestResult,
  },
  connections: {
    storeData: storeConnectionData.bind(null, cosmosClient),
    fetchData: fetchConnectionData.bind(null, cosmosClient),
    deleteData: deleteConnectionData.bind(null, cosmosClient),
    sendMessage: sendMessageToConnection,
  },
  // ScheduledCommandsLibrary
  scheduled: {
    rawToEnvelope: rawScheduledInputToEnvelope,
  },
  rockets: {
    rawToEnvelopes: rawRocketInputToEnvelope,
  },
  sensor: {
    databaseEventsHealthDetails: databaseEventsHealthDetails.bind(null, cosmosClient),
    databaseReadModelsHealthDetails: databaseReadModelsHealthDetails.bind(null, cosmosClient),
    isDatabaseEventUp: isDatabaseEventUp.bind(null, cosmosClient),
    areDatabaseReadModelsUp: areDatabaseReadModelsUp.bind(null, cosmosClient),
    databaseUrls: databaseUrl.bind(null, cosmosClient),
    graphQLFunctionUrl: graphqlFunctionUrl,
    isGraphQLFunctionUp: isGraphQLFunctionUp,
    rawRequestToHealthEnvelope: rawRequestToSensorHealth,
    areRocketFunctionsUp: areRocketFunctionsUp,
  },
  // ProviderInfrastructureGetter
  infrastructure: () => {
    const infrastructurePackageName = require('../package.json').name + '-infrastructure'
    let infrastructure: HasInfrastructure | undefined

    try {
      infrastructure = loadInfrastructurePackage(infrastructurePackageName)
    } catch (e) {
      throw new Error(
        `The Azure infrastructure package could not be loaded. The following error was thrown: ${e.message}. Please ensure that one of the following actions has been done:\n` +
          `  - It has been specified in your "devDependencies" section of your "package.json" file. You can do so by running 'npm install --save-dev ${infrastructurePackageName}'\n` +
          `  - Or it has been installed globally. You can do so by running 'npm install -g ${infrastructurePackageName}'`
      )
    }

    return infrastructure.Infrastructure(rockets)
  },
})

export * from './constants'
export * from './library/configuration-adapter'
