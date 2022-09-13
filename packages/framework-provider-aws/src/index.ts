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
    // TODO: Make this compatible with ES Modules
    // More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
    // eslint-disable-next-line unicorn/prefer-module
    require('aws-sdk')

    // TODO: Make this compatible with ES Modules
    // More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
    // eslint-disable-next-line unicorn/prefer-module
    const { Provider } = require('./setup')
    return Provider(rockets)
  } catch {
    return {
      // ProviderEventsLibrary
      events: {
        rawToEnvelopes: undefined as any,
        forEntitySince: undefined as any,
        latestEntitySnapshot: undefined as any,
        store: undefined as any,
        search: undefined as any,
        searchEntitiesIDs: undefined as any,
      },
      // ProviderReadModelsLibrary
      readModels: {
        rawToEnvelopes: undefined as any,
        fetch: undefined as any,
        search: undefined as any,
        store: undefined as any,
        delete: undefined as any,
        subscribe: undefined as any,
        fetchSubscriptions: undefined as any,
        deleteSubscription: undefined as any,
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
        storeData: undefined as any,
        fetchData: undefined as any,
        deleteData: undefined as any,
        sendMessage: undefined as any,
      },
      // ScheduledCommandsLibrary
      scheduled: {
        rawToEnvelope: undefined as any,
      },
      // ProviderInfrastructureGetter
      infrastructure: () =>
        // TODO: Make this compatible with ES Modules
        // More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
        // eslint-disable-next-line unicorn/prefer-module
        require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
    }
  }
}

export * from './constants'
