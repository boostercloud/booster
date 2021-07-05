import {
  ReadModelBeforeFunction,
  Class,
  CommandBeforeFunction,
  CommandInput,
  FilterFor,
  ReadModelInterface,
  UserEnvelope,
} from '@boostercloud/framework-types'

export const getReadModelFilters = (
  filters: FilterFor<Class<ReadModelInterface>>,
  beforeHooks: Array<ReadModelBeforeFunction>,
  user?: UserEnvelope
): FilterFor<ReadModelInterface> => {
  return beforeHooks.reduce((currentFilter, before) => before(currentFilter, user), filters)
}

export const applyBeforeFunctions = (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): CommandInput => {
  return beforeHooks.reduce((currentInput, before) => before(currentInput, currentUser), commandInput)
}
