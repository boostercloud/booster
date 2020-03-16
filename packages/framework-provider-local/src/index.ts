import { ProviderLibrary, ProviderInfrastructure } from '@boostercloud/framework-types'
import { rawSignUpDataToUserEnvelope } from './library/auth-adapter'
import { rawCommandToEnvelope, handleCommandResult } from './library/commands-adapter'
import { UserRegistry, EventRegistry } from './services'

export { User, LoginCredentials, SignUpUser, RegisteredUser, AuthenticatedUser } from './library/auth-adapter'
export * from './constants'
export * from './services'

const userRegistry = new UserRegistry()
const eventRegistry = new EventRegistry()

export const Provider: ProviderLibrary = {
  rawCommandToEnvelope: rawCommandToEnvelope.bind(null, userRegistry),
  handleCommandResult: handleCommandResult.bind(null, eventRegistry),
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleCommandError: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawEventsToEnvelopes: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  storeEvent: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  readEntityEventsSince: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  readEntityLatestSnapshot: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawReadModelRequestToEnvelope: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fetchReadModel: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  fetchAllReadModels: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  storeReadModel: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleReadModelResult: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  handleReadModelError: undefined as any,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rawSignUpDataToUserEnvelope,
  getInfrastructure: () =>
    require(require('../package.json').name + '-infrastructure').Infrastructure as ProviderInfrastructure,
}
