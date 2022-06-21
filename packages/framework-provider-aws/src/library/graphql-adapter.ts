import { APIGatewayProxyEvent } from 'aws-lambda'
import { BoosterConfig, GraphQLRequestEnvelope, GraphQLRequestEnvelopeError } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export async function rawGraphQLRequestToEnvelope(
  config: BoosterConfig,
  request: APIGatewayProxyEvent
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const logger = getLogger(config, 'graphql-adapter#rawGraphQLRequestToEnvelope')
  logger.debug('Received GraphQL request: ', request)
  const requestID = request.requestContext.requestId
  const eventType = (request.requestContext.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE'
  const connectionID = request.requestContext.connectionId
  try {
    let graphQLValue = undefined
    if (request.body) {
      graphQLValue = JSON.parse(request.body)
    }

    return {
      requestID,
      eventType,
      connectionID,
      token: request.headers?.Authorization,
      value: graphQLValue,
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
