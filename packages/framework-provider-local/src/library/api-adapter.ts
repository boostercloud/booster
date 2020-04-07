import { CommandResult } from '..'
import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'

export async function requestSucceeded(body?: any): Promise<CommandResult> {
  return {
    status: 'success',
    result: body,
  }
}

export async function requestFailed(error: Error): Promise<CommandResult> {
  const statusCode = httpStatusCodeFor(error)
  return {
    status: 'failure',
    code: statusCode,
    title: toClassTitle(error),
    reason: error.message,
  }
}
