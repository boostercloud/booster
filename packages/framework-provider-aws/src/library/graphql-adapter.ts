import { APIGatewayProxyEvent } from 'aws-lambda'
import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { userEnvelopeFromAuthToken } from './auth-adapter'

export async function rawGraphQLRequestToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent,
  logger: Logger
): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)
  let graphQLValue = undefined
  if (request.body) {
    graphQLValue = JSON.parse(request.body)
  }

  return {
    requestID: request.requestContext.requestId,
    eventType: (request.requestContext.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
    connectionID: request.requestContext.connectionId,
    currentUser: await userEnvelopeFromAuthToken(userPool, request.headers?.Authorization),
    value: graphQLValue,
  }
}
