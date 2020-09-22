import { GraphQLRequestEnvelope, GraphQLRequestEnvelopeError, Logger, UUID } from '@boostercloud/framework-types'
import * as express from 'express'

export async function rawGraphQLRequestToEnvelope(
  request: express.Request,
  logger: Logger
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  logger.debug('Received GraphQL request: \n- Headers: ', request.headers, '\n- Body: ', request.body)
  const requestID = UUID.generate() // TODO: Retrieve request ID from request
  const eventType = 'MESSAGE' // TODO: (request.requestContext?.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
  const connectionID = undefined // TODO: Retrieve connectionId if available,
  try {
    return {
      requestID,
      eventType,
      connectionID,
      currentUser: {
        username: 'test@test.com',
        role: '',
      },
      value: request.body,
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
