import {
  BoosterConfig,
  EventType,
  GraphQLClientMessage,
  GraphQLOperation,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import * as express from 'express'
import { ExpressWebSocketMessage } from './web-socket-server-adapter'

export async function rawGraphQLRequestToEnvelope(
  config: BoosterConfig,
  request: express.Request | ExpressWebSocketMessage
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const requestID = UUID.generate()
  return isExpressWebSocketMessage(request)
    ? expressWebSocketMessageToEnvelope(config, request, requestID)
    : expressHttpMessageToEnvelope(config, request, requestID)
}

function expressWebSocketMessageToEnvelope(
  config: BoosterConfig,
  webSocketRequest: ExpressWebSocketMessage,
  requestID: UUID
): GraphQLRequestEnvelope | GraphQLRequestEnvelopeError {
  const logger = getLogger(config, 'graphql-adapter#expressWebSocketMessageToEnvelope')
  logger.debug('Received  WebSocket GraphQL request: ', webSocketRequest)

  let eventType: EventType = 'MESSAGE'
  const incomingMessage = webSocketRequest.incomingMessage
  const headers = incomingMessage?.headers
  const data = webSocketRequest.data as GraphQLOperation | GraphQLClientMessage | undefined
  try {
    const connectionContext = webSocketRequest.connectionContext
    eventType = connectionContext?.eventType
    return {
      requestID,
      eventType,
      connectionID: connectionContext?.connectionId.toString(),
      token: headers?.authorization,
      value: data,
      context: {
        request: {
          headers: headers,
          body: data,
        },
        rawContext: webSocketRequest,
      },
    }
  } catch (e) {
    return {
      error: e,
      requestID,
      connectionID: undefined,
      eventType: eventType,
      context: {
        request: {
          headers: headers,
          body: data,
        },
        rawContext: webSocketRequest,
      },
    } as GraphQLRequestEnvelopeError
  }
}

function expressHttpMessageToEnvelope(
  config: BoosterConfig,
  httpRequest: express.Request,
  requestId: UUID
): GraphQLRequestEnvelope | GraphQLRequestEnvelopeError {
  const logger = getLogger(config, 'graphql-adapter#expressHttpMessageToEnvelope')
  const eventType: EventType = 'MESSAGE'
  const headers = httpRequest.headers
  const data = httpRequest.body
  try {
    logger.debug('Received GraphQL request: \n- Headers: ', headers, '\n- Body: ', data)
    return {
      connectionID: undefined,
      requestID: requestId,
      eventType: eventType,
      token: headers?.authorization,
      value: data,
      context: {
        request: {
          headers: headers,
          body: data,
        },
        rawContext: httpRequest,
      },
    }
  } catch (e) {
    return {
      error: e,
      requestID: requestId,
      connectionID: undefined,
      eventType: eventType,
      context: {
        request: {
          headers: headers,
          body: data,
        },
        rawContext: httpRequest,
      },
    } as GraphQLRequestEnvelopeError
  }
}

function isExpressWebSocketMessage(
  request: express.Request | ExpressWebSocketMessage
): request is ExpressWebSocketMessage {
  return 'connectionContext' in request
}
