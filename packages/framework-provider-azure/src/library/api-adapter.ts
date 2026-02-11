import { HttpResponseInit } from '@azure/functions'
import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'

/**
 * Standard HTTP response type for Azure Functions v4.
 * Uses HttpResponseInit from '@azure/functions' v4.
 */
export type AzureHttpResponse = HttpResponseInit

/**
 * Web PubSub response types for Azure Functions v4.
 * These are returned directly from Web PubSub triggered functions.
 */
export interface WebPubSubConnectResponse {
  subprotocol?: string
  userId?: string
  groups?: string[]
  roles?: string[]
}

export interface WebPubSubMessageResponse {
  data?: unknown
  dataType?: 'json' | 'text' | 'binary'
}

const WEB_SOCKET_PROTOCOL_HEADER = 'Sec-WebSocket-Protocol'
const WEB_SOCKET_MESSAGE_MARKER_HEADER = 'X-Booster-WebSocket-Message'

export async function requestSucceeded(
  body?: unknown,
  headers?: Record<string, number | string | ReadonlyArray<string>>
): Promise<AzureHttpResponse | WebPubSubConnectResponse | WebPubSubMessageResponse | void> {
  // Check if this is a Web Pubsub CONNECT event (has the specific WebSocket header)
  const isWebSocketConnect = headers && Object.keys(headers).includes(WEB_SOCKET_PROTOCOL_HEADER)

  // Web PubSub CONNECT event - return subprotocol response format
  if (isWebSocketConnect) {
    const subprotocol = headers[WEB_SOCKET_PROTOCOL_HEADER]
    return {
      subprotocol: Array.isArray(subprotocol) ? subprotocol[0] : String(subprotocol),
    } as WebPubSubConnectResponse
  }

  // Check if this is a Web Pubsub MESSAGE event (has the marker header from framework-core
  const isWebSocketMessage = headers && headers[WEB_SOCKET_MESSAGE_MARKER_HEADER] === 'true'

  // Web PubSub MESSAGE event - return data response format
  if (isWebSocketMessage) {
    return {
      data: body,
      dataType: 'json',
    } as WebPubSubMessageResponse
  }

  // No body and no meaningful headers - nothing to return (e.g., DISCONNECT)
  if (!body && (!headers || Object.keys(headers).length === 0)) {
    return
  }

  // Standard HTTP response for everything else
  const responseHeaders: Record<string, string> = {
    'Access-Control-Allow-Origin': '*',
    'Content-Type': 'application/json',
  }
  if (headers) {
    for (const [key, value] of Object.entries(headers)) {
      // Don't include internal Booster headers in HTTP response
      if (key.startsWith('X-Booster-')) continue
      responseHeaders[key] = Array.isArray(value) ? value.join(',') : String(value)
    }
  }

  const response: AzureHttpResponse = {
    headers: responseHeaders,
    status: 200,
  }

  if (body) {
    response.jsonBody = body
  }

  return response
}

export async function requestFailed(error: Error): Promise<AzureHttpResponse> {
  const status = httpStatusCodeFor(error)
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json',
    },
    status,
    jsonBody: {
      status,
      title: toClassTitle(error),
      reason: error.message,
    },
  }
}

export async function healthRequestResult(body: unknown, isHealthy: boolean): Promise<AzureHttpResponse> {
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json',
    },
    status: isHealthy ? 200 : 503,
    jsonBody: body,
  }
}
