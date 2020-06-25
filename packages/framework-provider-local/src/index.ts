import { ProviderLibrary, ProviderInfrastructure } from '@boostercloud/framework-types'
import { authorizeRequest, rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { storeEvents } from './library/events-adapter'
import { requestSucceeded, requestFailed } from './library/api-adapter'
import { EventRegistry } from './services'
import { rawGraphQLRequestToEnvelope } from './library/graphql-adapter'

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
    forEntitySince: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    latestEntitySnapshot: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    store: storeEvents.bind(null, eventRegistry),
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
    authorizeRequest: authorizeRequest,
    rawToEnvelope: rawGraphQLRequestToEnvelope,
    handleResult: requestSucceeded,
  },
  // ProviderAuthLibrary
  auth: {
    rawToEnvelope: rawSignUpDataToUserEnvelope,
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
