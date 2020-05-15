import { ProviderLibrary, ProviderInfrastructure } from '@boostercloud/framework-types'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { publishEvents } from './library/events-adapter'
import { requestSucceeded, requestFailed } from './library/api-adapter'
import { EventRegistry } from './services'

export { User, LoginCredentials, SignUpUser, RegisteredUser, AuthenticatedUser } from './library/auth-adapter'
export * from './paths'
export * from './services'

const eventRegistry = new EventRegistry()

export const Provider: ProviderLibrary = {
  // ProviderEventsLibrary
  events: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawToEnvelopes: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    store: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    forEntitySince: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    readEntityLatestSnapshot: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    publish: publishEvents.bind(null, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetch: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    search: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    subscribe: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawToEnvelopes: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetchSubscriptions: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    notifySubscription: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    store: undefined as any,
  },
  // ProviderGraphQLLibrary
  graphQL: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    authorizeRequest: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawToEnvelope: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    handleResult: undefined as any,
  },
  // ProviderAuthLibrary
  auth: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawToEnvelope: rawSignUpDataToUserEnvelope,
  },
  // ProviderAPIHandling
  api: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    requestSucceeded,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    requestFailed,
  },
  // ProviderInfrastructureGetter
  infrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}
