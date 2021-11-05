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
      token: request.headers.authorization,
      value: request.body,
    }
  } catch (err) {
    const e = err as Error
    return {
      error: e,
      requestID,
      connectionID,
      eventType,
    }
  }
}
