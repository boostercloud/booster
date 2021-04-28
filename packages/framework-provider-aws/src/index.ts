/* eslint-disable @typescript-eslint/no-explicit-any */
import { ProviderInfrastructure, ProviderLibrary, RocketDescriptor } from '@boostercloud/framework-types'
import { requestFailed, requestSucceeded } from './library/api-gateway-io'

/**
 * `Provider` is a function that accepts a list of rocket names and returns an
 * object compatible with the `ProviderLibrary` defined in the `framework-types` package.
 * The rocket names are passed to the infrastructure package, which loads them dynamically
 * to extend the AWS functionality. Rockets are typically distributed in separate node packages.
 */
export const Provider = (rockets?: RocketDescriptor[]): ProviderLibrary => {
  try {
    require('aws-sdk')
    const { Provider } = require('./setup.ts')
    return Provider(rockets)
  } catch (e) {
    return {
      // ProviderEventsLibrary
      events: {
        rawToEnvelopes: undefined as any,
        forEntitySince: undefined as any,
        latestEntitySnapshot: undefined as any,
        store: undefined as any,
        search: undefined as any,
      },
      // ProviderReadModelsLibrary
      readModels: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        rawToEnvelopes: undefined as any,
        fetch: undefined as any,
        search: undefined as any,
        store: undefined as any,
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
        rawToEnvelope: undefined as any,
        handleResult: undefined as any,
      },
      // ProviderAPIHandling
      api: {
        requestSucceeded,
        requestFailed,
      },
      connections: {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        storeData: undefined as any,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        fetchData: undefined as any,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        deleteData: undefined as any,
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        sendMessage: undefined as any,
      },
      // ScheduledCommandsLibrary
      scheduled: {
        rawToEnvelope: undefined as any,
      },
      // ProviderInfrastructureGetter
      infrastructure: () =>
        require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
    }
  }
}

export * from './constants'
