import { CognitoUserPoolEvent } from 'aws-lambda'
import { UserEnvelope } from '@boostercloud/framework-types'
import { UserEnvelopeBuilder } from './user-envelopes'

export function rawSignUpDataToUserEnvelope(rawMessage: CognitoUserPoolEvent): UserEnvelope {
  return UserEnvelopeBuilder.fromAttributeMap(rawMessage.request.userAttributes)
}
