import { CognitoUserPoolEvent } from 'aws-lambda'
import { UserEnvelope } from '@boostercloud/framework-types'
import { UserEnvelopeBuilder } from './user-envelopes'
import { BoosterConfig } from '@boostercloud/framework-types'
import { InvalidParameterError } from '@boostercloud/framework-types'

export function rawSignUpDataToUserEnvelope(rawMessage: CognitoUserPoolEvent): UserEnvelope {
  return UserEnvelopeBuilder.fromAttributeMap(rawMessage.request.userAttributes)
}

export function enrichRawMessage(config: BoosterConfig, rawMessage: CognitoUserPoolEvent): CognitoUserPoolEvent {
  const userAttributes = rawMessage.request.userAttributes
  const { phone_number: phoneNumber, email, 'custom:role': roleName } = userAttributes

  if (roleName) {
    const roleMetadata = config.roles[roleName]
    if (!roleMetadata) {
      throw new InvalidParameterError(`Unknown role ${roleName}`)
    }

    const skipConfirmation = roleMetadata?.auth?.skipConfirmation
    rawMessage.response.autoConfirmUser = skipConfirmation != undefined ? skipConfirmation : false

    if (email && !skipConfirmation) {
      rawMessage.response.autoVerifyEmail = false
    } else if (email && skipConfirmation) {
      rawMessage.response.autoVerifyEmail = true
    }

    if (phoneNumber && !skipConfirmation) {
      rawMessage.response.autoVerifyPhone = false
    } else if (phoneNumber && skipConfirmation) {
      rawMessage.response.autoVerifyPhone = true
    }
  }

  return rawMessage
}
