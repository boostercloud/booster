import { ReadModelInterface } from '.'
import { UserEnvelope, ReadModelRequestEnvelope } from '../envelope'
import { FilterFor } from '../searcher'
import { CommandInput } from './command'

export interface CommandFilterHooks {
  readonly before?: Array<CommandBeforeFunction>
}

export interface QueryFilterHooks {
  readonly before?: Array<CommandBeforeFunction>
}

export type CommandBeforeFunction = (input: CommandInput, currentUser?: UserEnvelope) => Promise<CommandInput>

export type QueryBeforeFunction = (
  input: Record<string, FilterFor<unknown>>,
  currentUser?: UserEnvelope
) => Promise<Record<string, FilterFor<unknown>>>

export interface ReadModelFilterHooks {
  readonly before?: Array<ReadModelBeforeFunction>
}

export type ReadModelBeforeFunction = (
  readModelRequestEnvelope: ReadModelRequestEnvelope<ReadModelInterface>,
  currentUser?: UserEnvelope
) => Promise<ReadModelRequestEnvelope<ReadModelInterface>>
