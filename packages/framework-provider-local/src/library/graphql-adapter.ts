import { GraphQLRequestEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import * as express from 'express'

export async function rawGraphQLRequestToEnvelope(
  request: express.Request,
  logger: Logger
): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)

  return {
    requestID: UUID.generate(), // TODO: Retrieve request ID from request
    eventType: 'MESSAGE', // TODO: (request.requestContext?.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
    connectionID: undefined, // TODO: Retrieve connectionId if available,
    currentUser: {
      username: 'test@test.com',
      role: '',
    },
    value: request.body,
  }
}
