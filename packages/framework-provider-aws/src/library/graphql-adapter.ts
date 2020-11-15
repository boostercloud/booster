import { APIGatewayProxyEvent } from 'aws-lambda'
import { GraphQLRequestEnvelope, GraphQLRequestEnvelopeError, Logger } from '@boostercloud/framework-types'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { userEnvelopeFromAuthToken } from './auth-adapter'

export async function rawGraphQLRequestToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent,
  logger: Logger
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
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
      currentUser: await userEnvelopeFromAuthToken(userPool, request.headers?.Authorization),
      value: graphQLValue,
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
