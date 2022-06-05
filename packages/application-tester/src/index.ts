import { RocketDescriptor, ProviderLibrary } from '@boostercloud/framework-types'
import { events } from './provider'

export * from './integration-test-helpers'

export const Provider = (rockets?: RocketDescriptor[]): ProviderLibrary => {
  return {
    events,
    readModels: undefined as any,
    graphQL: undefined as any,
    api: undefined as any,
    connections: undefined as any,
    scheduled: undefined as any,
    infrastructure: undefined as any,
  }
}
