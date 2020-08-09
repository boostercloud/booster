import { ProviderLibrary, ProviderInfrastructure } from '@boostercloud/framework-types'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import {
  rawEventsToEnvelopes,
  readEntityEventsSince,
  readEntityLatestSnapshot,
  storeEvents,
} from './library/events-adapter'
import { requestSucceeded, requestFailed } from './library/api-adapter'
import { EventRegistry } from './services'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'
import { notifySubscription } from './library/subscription-adapter'
import { UserApp } from '@boostercloud/framework-types'
import * as path from 'path'

export { User, LoginCredentials, SignUpUser, RegisteredUser, AuthenticatedUser } from './library/auth-adapter'
export * from './paths'
export * from './services'

const eventRegistry = new EventRegistry()
const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))

export const Provider: ProviderLibrary = {
  // ProviderEventsLibrary
  events: {
    rawToEnvelopes: rawEventsToEnvelopes,
    forEntitySince: readEntityEventsSince.bind(null, eventRegistry),
    latestEntitySnapshot: readEntityLatestSnapshot.bind(null, eventRegistry),
    store: storeEvents.bind(null, userApp, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawToEnvelopes: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetch: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    search: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    store: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    subscribe: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetchSubscriptions: undefined as any,
    notifySubscription: notifySubscription,
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
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: rawSignUpDataToUserEnvelope,
    enrichRawMessage: undefined as any,
  },
  // ProviderAPIHandling
  api: {
    requestSucceeded,
    requestFailed,
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}
