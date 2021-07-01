import { UserEnvelope } from '../envelope'
import { CommandInput } from './command'

export interface CommandFilterHooks {
  readonly before?: Array<CommandBeforeFunction>
}

export type CommandBeforeFunction = (input: CommandInput, currentUser?: UserEnvelope) => CommandInput
