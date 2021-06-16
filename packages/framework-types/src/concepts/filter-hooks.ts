import { UserEnvelope } from '../envelope'
import { CommandInput } from './command'

export interface FilterHooks {
  readonly beforeCommand?: Array<CommandBeforeFunction>
}

export type CommandBeforeFunction = (input: CommandInput, currentUser?: UserEnvelope) => CommandInput
