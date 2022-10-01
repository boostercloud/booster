import {
  ReadModelBeforeFunction,
  CommandBeforeFunction,
  CommandInput,
  UserEnvelope,
  ReadModelRequestEnvelope,
  ReadModelInterface,
  FilterFor,
  QueryArgs,
} from '@boostercloud/framework-types'
import { QueryResult } from 'framework-types/dist/concepts/query'

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

export const applyQueryBeforeFunctions = async (
  queryInput: Record<string, FilterFor<QueryResult>>,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<QueryArgs> => {
  let result = queryInput
  for (const beforeHook of beforeHooks) {
    result = await beforeHook(result, currentUser)
  }
  return result
}

export const applyBeforeFunctions = async (
  commandInput: CommandInput,
  beforeHooks: Array<CommandBeforeFunction>,
  currentUser?: UserEnvelope
): Promise<CommandInput> => {
  let result = commandInput
  for (const beforeHook of beforeHooks) {
    result = await beforeHook(result, currentUser)
  }
  return result
}
