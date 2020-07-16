import { APIGatewayProxyWithLambdaAuthorizerEvent } from 'aws-lambda'
import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'
import { AuthorizerWithUserData } from './auth-adapter'
import { fetchUserFromRequest } from './user-envelopes'
import { CognitoIdentityServiceProvider } from 'aws-sdk'

export async function rawGraphQLRequestToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyWithLambdaAuthorizerEvent<AuthorizerWithUserData>,
  logger: Logger
): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)
  let graphQLValue = undefined
  let user
  if (request.body) {
    graphQLValue = JSON.parse(request.body)
    user = await fetchUserFromRequest(userPool, request, graphQLValue?.payload?.Authorization)
  }

  return {
    requestID: request.requestContext.requestId,
    eventType: (request.requestContext.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
    connectionID: request.requestContext.connectionId,
    currentUser: user,
    value: graphQLValue,
  }
}
