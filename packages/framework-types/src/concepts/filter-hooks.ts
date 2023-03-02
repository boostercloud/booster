import { ReadModelInterface } from '.'
import { UserEnvelope, ReadModelRequestEnvelope } from '../envelope'
import { CommandInput } from './command'

export interface CommandFilterHooks {
  readonly before?: Array<CommandBeforeFunction>
}

export type CommandBeforeFunction = (input: CommandInput, currentUser?: UserEnvelope) => Promise<CommandInput>

export interface ReadModelFilterHooks<TReadModel extends ReadModelInterface = ReadModelInterface> {
  readonly before?: Array<ReadModelBeforeFunction<TReadModel>>
}

export type ReadModelBeforeFunction<TReadModel extends ReadModelInterface = ReadModelInterface> = (
  readModelRequestEnvelope: ReadModelRequestEnvelope<TReadModel>,
  currentUser?: UserEnvelope
) => Promise<ReadModelRequestEnvelope<TReadModel>>
