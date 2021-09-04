import {
  ReadModelBeforeFunction,
  CommandBeforeFunction,
  CommandInput,
  UserEnvelope,
  ReadModelRequestEnvelope,
  ReadModelInterface,
} from '@boostercloud/framework-types'

export const applyReadModelRequestBeforeFunctions = (
  readModelRequestEnvelope: ReadModelRequestEnvelope<ReadModelInterface>,
  beforeHooks: Array<ReadModelBeforeFunction>
): ReadModelRequestEnvelope<ReadModelInterface> => {
  return beforeHooks.reduce(
    (currentReadModelRequestEnvelope, beforeFunction) => beforeFunction(currentReadModelRequestEnvelope),
    readModelRequestEnvelope
  )
}

export const applyBeforeFunctions = (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): CommandInput => {
  return beforeHooks.reduce((currentInput, before) => before(currentInput, currentUser), commandInput)
}
