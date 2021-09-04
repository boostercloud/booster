import {
  ReadModelBeforeFunction,
  CommandBeforeFunction,
  CommandInput,
  UserEnvelope,
  ReadModelRequestEnvelope,
  ReadModelInterface,
} from '@boostercloud/framework-types'

export const applyReadModelRequestBeforeFunctions = async (
  readModelRequestEnvelope: ReadModelRequestEnvelope<ReadModelInterface>,
  beforeHooks: Array<ReadModelBeforeFunction>
): ReadModelRequestEnvelope<ReadModelInterface> => {
  return beforeHooks.reduce(
    async (currentReadModelRequestEnvelope, beforeFunction) => beforeFunction(await currentReadModelRequestEnvelope),
    readModelRequestEnvelope
  )
}

export const applyBeforeFunctions = async (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<CommandInput> => {
  return beforeHooks.reduce(async (currentInput, before) => before(await currentInput, currentUser), commandInput)
}
