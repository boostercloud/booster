import { GraphQLRequestEnvelope, Logger, GraphQLRequestEnvelopeError } from '@boostercloud/framework-types'
import { Context } from '@azure/functions'

export async function rawGraphQLRequestToEnvelope(
  context: Context,
  logger: Logger
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  logger.debug('Received GraphQL request: ', context.req)
  const requestID = context.executionContext.invocationId
  const connectionID = undefined // TODO: Add this when sockets are supported
  const eventType = 'MESSAGE'
  try {
    let graphQLValue = undefined
    if (context.req) {
      graphQLValue = context.req.body
    }

    return {
      requestID,
      eventType,
      token: context.req?.headers?.authorization,
      value: graphQLValue,
      context: {
        request: {
          headers: context.req?.headers,
          body: context.req?.body,
        },
        rawContext: context,
      },
    }
  } catch (e) {
    return {
      error: e,
      requestID,
      connectionID,
      eventType,
      context: {
        request: {
          headers: context.req?.headers,
          body: context.req?.body,
        },
        rawContext: context,
      },
    }
  }
}
