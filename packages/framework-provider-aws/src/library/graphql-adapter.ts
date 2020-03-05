import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayAuthorizerWithContextResult, APIGatewayProxyWithLambdaAuthorizerEvent } from 'aws-lambda'
import { fetchUserFromRequest } from './user-envelopes'
import { GraphQLRequestEnvelope, Logger, UserEnvelope } from '@boostercloud/framework-types'
import { APIGatewayRequestAuthorizerEvent } from 'aws-lambda/trigger/api-gateway-authorizer'

type AuthorizerWithUserData = {
  userJSON: string
}

export async function authorizeRequest(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayRequestAuthorizerEvent & { methodArn: string },
  logger: Logger
): Promise<APIGatewayAuthorizerWithContextResult<AuthorizerWithUserData>> {
  logger.debug('Received an authorization request: ', request)
  let user: UserEnvelope | undefined
  try {
    user = await fetchUserFromRequest(request, userPool)
  } catch (e) {
    /* We simple leave the user empty */
  }
  return {
    principalId: user?.email ?? 'anonymous',
    policyDocument: {
      Version: '2012-10-17',
      Statement: [
        {
          Action: 'execute-api:Invoke',
          Effect: 'Allow',
          Resource: request.methodArn,
        },
      ],
    },
    context: {
      userJSON: JSON.stringify(user ?? null),
    },
  }
}
export async function rawGraphQLRequestToEnvelope(
  request: APIGatewayProxyWithLambdaAuthorizerEvent<AuthorizerWithUserData>,
  logger: Logger
): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)
  return {
    requestID: request.requestContext.requestId,
    eventType: request.requestContext.eventType as GraphQLRequestEnvelope['eventType'],
    connectionID: request.requestContext.connectionId as string,
    currentUser: JSON.parse(request.requestContext.authorizer.userJSON),
    value: request.body ?? undefined,
  }
}
