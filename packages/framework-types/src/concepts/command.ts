import { Register } from './register'
import { Class } from '../typelevel'
import { RoleAccess } from './role'

/**
 * A `Command` is an imperative intent in your system.
 * All Command classes of your application must extend this class.
 */
export interface CommandInterface {
  handle(register: Register): void
}

export interface CommandMetadata {
  readonly class: Class<CommandInterface>
  readonly authorizedRoles: RoleAccess['authorize']
}
