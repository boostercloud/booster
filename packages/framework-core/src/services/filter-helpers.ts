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

export const applyBeforeFunctions = async (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<CommandInput> => {
  return beforeHooks.reduce(async (currentInputPromise, before) => {
    const currentInput = await currentInputPromise
    return Promise.resolve(before(currentInput, currentUser))
  }, Promise.resolve(commandInput))
}
