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

const WEB_SOCKET_PROTOCOL_HEADER = 'Sec-WebSocket-Protocol'

export async function requestSucceeded(
  body?: unknown,
  headers?: Record<string, number | string | ReadonlyArray<string>>
): Promise<ContextResponse | void> {
  if (!body && (!headers || Object.keys(headers).length === 0)) {
    return
  }
  const isWebSocket = headers && Object.keys(headers).includes(WEB_SOCKET_PROTOCOL_HEADER)
  let extraParams: Record<string, unknown> = {}
  if (isWebSocket) {
    extraParams = { Subprotocol: headers[WEB_SOCKET_PROTOCOL_HEADER] }
  }
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json',
      ...headers,
    },
    status: 200,
    body: body ? JSON.stringify(body) : '',
    ...extraParams,
  }
}

export async function requestFailed(error: Error): Promise<ContextResponse> {
  const status = httpStatusCodeFor(error)
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json',
    },
    status,
    body: JSON.stringify({
      status,
      title: toClassTitle(error),
      reason: error.message,
    }),
  }
}
