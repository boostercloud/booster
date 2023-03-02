import { BoosterConfig, GraphQLRequestEnvelope, GraphQLRequestEnvelopeError, UUID } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import * as express from 'express'

export async function rawGraphQLRequestToEnvelope(
  config: BoosterConfig,
  request: express.Request
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const logger = getLogger(config, 'graphql-adapter#rawGraphQLRequestToEnvelope')
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
      context: {
        request: {
          headers: request.headers,
          body: request.body,
        },
        rawContext: request,
      },
    }
  } catch (e) {
    const error = e as Error
    return {
      error,
      requestID,
      connectionID,
      eventType,
      context: {
        request: {
          headers: request.headers,
          body: request.body,
        },
        rawContext: request,
      },
    }
  }
}
