import { UserEnvelope } from '../envelope'
import { CommandInput } from './command'
import { FilterFor } from '../searcher'
import { Class } from '../typelevel'
import { ReadModelInterface } from './read-model'

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
  filter: FilterFor<Class<ReadModelInterface>>,
  currentUser?: UserEnvelope
) => FilterFor<Class<ReadModelInterface>> | Promise<FilterFor<Class<ReadModelInterface>>>
