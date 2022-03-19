import {
  ReadModelBeforeFunction,
  CommandBeforeFunction,
  CommandInput,
  UserEnvelope,
  Register,
  CommandAfterFunction,
  ReadModelRequestEnvelope,
  ReadModelInterface,
} from '@boostercloud/framework-types'

export const applyReadModelRequestBeforeFunctions = async (
  readModelRequestEnvelope: ReadModelRequestEnvelope<ReadModelInterface>,
  beforeHooks: Array<ReadModelBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<ReadModelRequestEnvelope<ReadModelInterface>> => {
  let result = readModelRequestEnvelope
  for (const beforeHook of beforeHooks) {
    result = await beforeHook(result, currentUser)
  }
  return result
}

export const applyBeforeFunctions = async (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  register: Register
): Promise<CommandInput> => {
  let result = commandInput
  for (const beforeHook of beforeHooks) {
    result = await beforeHook(result, register)
  }
  return result
}

export const applyAfterFunctions = async (
  result: unknown,
  commandInput: CommandInput,
  afterHooks: Array<CommandAfterFunction>,
  register: Register
): Promise<void> => {
  let previousResult = result
  for (const afterHook of afterHooks) {
    previousResult = await afterHook(previousResult, commandInput, register)
  }
}
