import { ReadModelInterface, Register } from '.'
import { UserEnvelope, ReadModelRequestEnvelope } from '../envelope'
import { CommandInput } from './command'

export interface CommandFilterHooks {
  readonly before?: Array<CommandBeforeFunction>
  readonly after?: Array<CommandAfterFunction>
  readonly onError?: CommandErrorFunction | undefined
}

export type CommandBeforeFunction = (input: CommandInput, register: Register) => Promise<CommandInput>

export type CommandAfterFunction = (previousResult: unknown, input: CommandInput, register: Register) => Promise<void>

export type CommandErrorFunction = (e: Error, input: CommandInput, register: Register) => Promise<Error>

export interface ReadModelFilterHooks {
  readonly before?: Array<ReadModelBeforeFunction>
}

export type ReadModelBeforeFunction = (
  readModelRequestEnvelope: ReadModelRequestEnvelope<ReadModelInterface>,
  currentUser?: UserEnvelope
) => Promise<ReadModelRequestEnvelope<ReadModelInterface>>
