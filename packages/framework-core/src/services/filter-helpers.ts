import { BeforeFunction, Class, FilterFor, ReadModelInterface, UserEnvelope } from '@boostercloud/framework-types'

export const getReadModelFilters = (
  filters: FilterFor<Class<ReadModelInterface>>,
  beforeHooks: Array<BeforeFunction>,
  user?: UserEnvelope
): FilterFor<ReadModelInterface> => {
  return beforeHooks.reduce((currentFilter, before) => before(currentFilter, user), filters)
}
