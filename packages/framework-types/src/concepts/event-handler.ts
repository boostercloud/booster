import { Register } from './register'
import { Class } from '../typelevel'

export interface EventHandlerInterface<TEventHandler = unknown> extends Class<TEventHandler> {
  handle(event: TEventHandler, register: Register): Promise<void>
}

/*export interface EventHandlerMetadata<TEventHandler = unknown> {
  // For the class, we care that it has the static methods specified by CommandInterface
  // and it has at least the properties of a class (like name, constructor, etc...)
  // We don't care about the properties of the instance, so we set the type parameter of
  // Class to unknown.
  readonly class: EventHandlerInterface<TEventHandler>
  readonly properties: Array<PropertyMetadata>
  readonly authorizedRoles: RoleAccess['authorize']
}*/
