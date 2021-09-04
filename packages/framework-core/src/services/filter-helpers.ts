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
): Promise<ReadModelRequestEnvelope<ReadModelInterface>> => {
  return beforeHooks.reduce<Promise<ReadModelRequestEnvelope<ReadModelInterface>>>(
    async (currentReadModelRequestEnvelopePromise, beforeFunction) =>
      beforeFunction(await currentReadModelRequestEnvelopePromise),
    Promise.resolve(readModelRequestEnvelope)
  )
}

export const applyBeforeFunctions = async (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<CommandInput> => {
  return beforeHooks.reduce(
    async (currentInputPromise, before) => before(await currentInputPromise, currentUser),
    Promise.resolve(commandInput)
  )
}
