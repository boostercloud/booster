import { CognitoUserPoolEvent } from 'aws-lambda'
import { BoosterConfig, UserEnvelope } from '@boostercloud/framework-types'
import { UserEnvelopeBuilder } from './user-envelopes'
import { CognitoIdentityServiceProvider } from 'aws-sdk'

export function rawSignUpDataToUserEnvelope(rawMessage: CognitoUserPoolEvent): UserEnvelope {
  return UserEnvelopeBuilder.fromAttributeMap(rawMessage.request.userAttributes)
}

export async function userEnvelopeFromAuthToken(
  userPool: CognitoIdentityServiceProvider,
  token: string | undefined
): Promise<UserEnvelope | undefined> {
  if (!token) {
    return undefined
  }
  const accessToken = token.replace('Bearer ', '')
  const currentUserData = await userPool.getUser({ AccessToken: accessToken }).promise()
  // TODO: Check the errors for when the token is expired, etc. and throw with corresponding types and then use HTTP status codes
  return UserEnvelopeBuilder.fromAttributeList(currentUserData.UserAttributes)
}

export function handleSignUpResult(
  config: BoosterConfig,
  rawMessage: CognitoUserPoolEvent,
  userEnvelope: UserEnvelope
): CognitoUserPoolEvent {
  const roleMetadata = config.roles[userEnvelope.role]
  const skipConfirmation = roleMetadata?.auth?.skipConfirmation ?? false
  rawMessage.response.autoConfirmUser = skipConfirmation

  return rawMessage
}
