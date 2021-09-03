import {
  ReadModelBeforeFunction,
  Class,
  CommandBeforeFunction,
  CommandInput,
  FilterFor,
  ReadModelInterface,
  UserEnvelope,
} from '@boostercloud/framework-types'

export const getReadModelFilters = async (
  filters: FilterFor<Class<ReadModelInterface>>,
  beforeHooks: Array<ReadModelBeforeFunction>,
  user?: UserEnvelope
): Promise<FilterFor<ReadModelInterface>> => {
  return beforeHooks.reduce(async (currentFilter, before) => before(await currentFilter, user), filters)
}

export const applyBeforeFunctions = async (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<CommandInput> => {
  return beforeHooks.reduce(async (currentInput, before) => before(await currentInput, currentUser), commandInput)
}
