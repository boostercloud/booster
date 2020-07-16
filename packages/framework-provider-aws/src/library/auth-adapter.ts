import { APIGatewayAuthorizerWithContextResult, CognitoUserPoolEvent } from 'aws-lambda'
import { UserEnvelope, Logger } from '@boostercloud/framework-types'
import { fetchUserFromRequest, UserEnvelopeBuilder } from './user-envelopes'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayRequestAuthorizerEvent } from 'aws-lambda/trigger/api-gateway-authorizer'

export type AuthorizerWithUserData = {
  userJSON: string
}

export async function authorizeRequest(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayRequestAuthorizerEvent & { methodArn: string },
  logger: Logger
): Promise<APIGatewayAuthorizerWithContextResult<AuthorizerWithUserData>> {
  logger.debug('Received an authorization request: ', request)
  const user = await fetchUserFromRequest(userPool, request)
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

export function rawSignUpDataToUserEnvelope(rawMessage: CognitoUserPoolEvent): UserEnvelope {
  return UserEnvelopeBuilder.fromAttributeMap(rawMessage.request.userAttributes)
}
