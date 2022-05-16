import { HasInfrastructure, ProviderLibrary, RocketDescriptor, UserApp } from '@boostercloud/framework-types'
import {
  rawEventsToEnvelopes,
  readEntityEventsSince,
  readEntityLatestSnapshot,
  storeEvents,
} from './library/events-adapter'
import { requestSucceeded, requestFailed } from './library/api-adapter'
import { EventRegistry, ReadModelRegistry } from './services'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'

import * as path from 'path'

import {
  fetchReadModel,
  rawReadModelEventsToEnvelopes,
  searchReadModel,
  storeReadModel,
} from './library/read-model-adapter'
import { searchEventsIds, searchEvents } from './library/events-search-adapter'
import { rawScheduledInputToEnvelope } from './library/scheduled-adapter'

export * from './paths'
export * from './services'

const eventRegistry = new EventRegistry()
const readModelRegistry = new ReadModelRegistry()
const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))

/* We load the infrastructure package dynamically here to avoid including it in the
 * dependencies that are deployed in the lambda functions. The infrastructure
 * package is only used during the deploy.
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
    search: searchEvents.bind(null, eventRegistry),
    searchEventsIds: searchEventsIds.bind(null, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawToEnvelopes: rawReadModelEventsToEnvelopes,
    fetch: fetchReadModel.bind(null, readModelRegistry),
    search: searchReadModel.bind(null, readModelRegistry),
    store: storeReadModel.bind(null, readModelRegistry),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    subscribe: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetchSubscriptions: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    deleteSubscription: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    deleteAllSubscriptions: undefined as any,
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
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    storeData: notImplemented as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetchData: notImplemented as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    deleteData: notImplemented as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    sendMessage: notImplemented as any,
  },
  // ScheduledCommandsLibrary
  scheduled: {
    rawToEnvelope: rawScheduledInputToEnvelope,
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

function notImplemented(): void {}
