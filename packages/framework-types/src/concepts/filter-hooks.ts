import { ReadModelInterface } from '.'
import { UserEnvelope, ReadModelRequestEnvelope } from '../envelope'
import { CommandInput } from './command'

export interface CommandFilterHooks {
  readonly before?: Array<CommandBeforeFunction>
}

export type CommandBeforeFunction = (
  input: CommandInput,
  currentUser?: UserEnvelope
) => CommandInput | Promise<CommandInput>

export interface ReadModelFilterHooks {
  readonly before?: Array<ReadModelBeforeFunction>
}

export type ReadModelBeforeFunction = (
  readModelRequestEnvelope: ReadModelRequestEnvelope<ReadModelInterface>,
  currentUser?: UserEnvelope
) => ReadModelRequestEnvelope<ReadModelInterface> | Promise<ReadModelRequestEnvelope<ReadModelInterface>>
