import { CognitoUserPoolEvent } from 'aws-lambda'
import { BoosterConfig, UserEnvelope } from '@boostercloud/framework-types'
import { UserEnvelopeBuilder } from './user-envelopes'

export function rawSignUpDataToUserEnvelope(rawMessage: CognitoUserPoolEvent): UserEnvelope {
  return UserEnvelopeBuilder.fromAttributeMap(rawMessage.request.userAttributes)
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
