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
    rawEventsToEnvelopes: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    storeEvent: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    readEntityEventsSince: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    readEntityLatestSnapshot: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    publishEvents: publishEvents.bind(null, eventRegistry),
  },
  // ProviderReadModelsLibrary
  readModels: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetchReadModel: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    searchReadModel: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    subscribeToReadModel: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawReadModelEventsToEnvelopes: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    fetchSubscriptions: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    notifySubscription: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    storeReadModel: undefined as any,
  },
  // ProviderGraphQLLibrary
  graphQL: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    authorizeRequest: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawGraphQLRequestToEnvelope: undefined as any,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    handleGraphQLResult: undefined as any,
  },
  // ProviderAuthLibrary
  auth: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawSignUpDataToUserEnvelope,
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
