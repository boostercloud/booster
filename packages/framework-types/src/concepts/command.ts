import { Class } from '../typelevel'
import { PropertyMetadata } from '@boostercloud/metadata-booster'
import { Register, CommandAuthorizer, CommandFilterHooks } from './.'

export type CommandInput = Record<string, any>

export interface CommandInterface<TCommand = unknown, THandleResult = unknown> extends Class<TCommand> {
  // The command's type is `unknown` because the CommandInterface type specifies the
  // structure of the class, rather than the instance of the commands, which is what
  // arrives to the `handle` static method.
  handle(command: TCommand, register: Register): Promise<THandleResult>
}

// We set the TCommand type to `unknown` because at the time of execution of the
// command handlers, we don't really know what's the type, nor we do care about it.
// The type correctness is ensured by the decorator, which ensures all of this.
export interface CommandMetadata<TCommand = unknown> {
  // For the class, we care that it has the static methods specified by CommandInterface
  // and it has at least the properties of a class (like name, constructor, etc...)
  // We don't care about the properties of the instance, so we set the type parameter of
  // Class to unknown.
  readonly class: CommandInterface<TCommand>
  readonly properties: Array<PropertyMetadata>
  readonly methods: Array<PropertyMetadata>
  readonly authorizer: CommandAuthorizer
  readonly before: NonNullable<CommandFilterHooks['before']>
}
