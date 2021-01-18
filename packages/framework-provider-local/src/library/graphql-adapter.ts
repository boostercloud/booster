import {
  GraphQLOperation,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  Logger,
  ReadModelPubSub,
  UUID,
} from '@boostercloud/framework-types'
import * as express from 'express'
import { PubSub } from 'graphql-subscriptions'

export type GraphQLSocketMessage = {
  connectionId?: string
  type: string
  payload: unknown
}

type Request = express.Request | GraphQLSocketMessage

export class GraphQLAdapter {
  private pubsub = (new PubSub() as unknown) as ReadModelPubSub

  public async rawGraphQLRequestToEnvelope(
    request: Request,
    logger: Logger
  ): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
    const requestID = UUID.generate() // TODO: Retrieve request ID from request
    let eventType: 'CONNECT' | 'MESSAGE' | 'DISCONNECT' = 'MESSAGE'
    let connectionID: string | undefined
    try {
      let value: unknown
      if (isWebsocketMessage(request)) {
        connectionID = request.connectionId
        switch (request.type) {
          case 'connection_init':
            eventType = 'CONNECT'
            break

          case 'complete':
            eventType = 'DISCONNECT'
            break

          default:
            eventType = 'MESSAGE'
            break
        }
        value = request
      } else {
        eventType = 'MESSAGE' // TODO: (request.requestContext?.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
        logger.debug('Received GraphQL request: \n- Headers: ', request.headers, '\n- Body: ', request.body)
        value = request.body
      }
      return {
        requestID,
        eventType,
        connectionID,
        currentUser: {
          username: 'test@test.com',
          role: '',
        },
        value: value as GraphQLOperation,
      }
    } catch (e) {
      return {
        error: e,
        requestID,
        connectionID,
        eventType,
      }
    }
  }

  public getPubSub(): ReadModelPubSub {
    return this.pubsub
  }
}

function isWebsocketMessage(request: Request): request is GraphQLSocketMessage {
  return (request as GraphQLSocketMessage).type !== undefined
}
