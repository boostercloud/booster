import { Cookie } from '@azure/functions'
import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'

/**
 * See https://docs.microsoft.com/es-es/azure/azure-functions/functions-reference-node#response-object
 */
export interface ContextResponse {
  body: string
  headers: object
  isRaw?: boolean
  status: number
  cookies?: Cookie[]
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function requestSucceeded(body?: any): Promise<ContextResponse> {
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    status: 200,
    body: body ? JSON.stringify(body) : '',
  }
}

export async function requestFailed(error: Error): Promise<ContextResponse> {
  const status = httpStatusCodeFor(error)
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    status,
    body: JSON.stringify({
      status,
      title: toClassTitle(error),
      reason: error.message,
    }),
  }
}
