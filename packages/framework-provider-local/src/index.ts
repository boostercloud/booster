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
import { UserApp } from '@boostercloud/framework-types'
import * as path from 'path'

export { User, LoginCredentials, SignUpUser, RegisteredUser, AuthenticatedUser } from './library/auth-adapter'
export * from './paths'
export * from './services'

const eventRegistry = new EventRegistry()
const userApp: UserApp = require(path.join(process.cwd(), 'dist', 'index.js'))

export const Provider = (): ProviderLibrary => ({
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
    rawToEnvelope: rawGraphQLRequestToEnvelope,
    handleResult: requestSucceeded,
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: rawSignUpDataToUserEnvelope,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fromAuthToken: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    handleSignUpResult: (() => {}) as any,
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
    rawToEnvelope: undefined as any,
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
})

function notImplemented(): void {}
