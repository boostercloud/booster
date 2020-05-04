import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'

export type APIResult =
  | { status: 'success'; result: unknown }
  | { status: 'failure'; code: number; title: string; reason: string }

export async function requestSucceeded(body?: any): Promise<APIResult> {
  return {
    status: 'success',
    result: body,
  }
}

export async function requestFailed(error: Error): Promise<APIResult> {
  const statusCode = httpStatusCodeFor(error)
  return {
    status: 'failure',
    code: statusCode,
    title: toClassTitle(error),
    reason: error.message,
  }
}
