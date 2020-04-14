import { ProviderLibrary, ProviderInfrastructure } from '@boostercloud/framework-types'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { rawCommandToEnvelope } from './library/commands-adapter'
import { publishEvents } from './library/events-adapter'
import { requestSucceeded, requestFailed } from './library/api-adapter'
import { UserRegistry, EventRegistry } from './services'

export { User, LoginCredentials, SignUpUser, RegisteredUser, AuthenticatedUser } from './library/auth-adapter'
export * from './paths'
export * from './services'
export { CommandResult } from './library/commands-adapter'

const userRegistry = new UserRegistry()
const eventRegistry = new EventRegistry()

export const Provider: ProviderLibrary = {
  // ProviderCommandsLibrary
  rawCommandToEnvelope: rawCommandToEnvelope.bind(null, userRegistry),

  // ProviderEventsLibrary
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

  // ProviderReadModelsLibrary
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawReadModelRequestToEnvelope: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fetchReadModel: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fetchAllReadModels: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  searchReadModel: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  subscribeToReadModel: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawReadModelEventsToEnvelopes: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fetchSubscriptions: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  storeReadModel: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleReadModelResult: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleReadModelError: undefined as any,

  // ProviderGraphQLLibrary
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  authorizeRequest: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawGraphQLRequestToEnvelope: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleGraphQLResult: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleGraphQLError: undefined as any,

  // ProviderAuthLibrary
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawSignUpDataToUserEnvelope,

  // ProviderAPIHandling
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  requestSucceeded,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  requestFailed,

  // ProviderInfrastructureGetter
  getInfrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}
