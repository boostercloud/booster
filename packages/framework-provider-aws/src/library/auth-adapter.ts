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

    const requiresConfirmation = roleMetadata?.auth?.requiresConfirmation
    rawMessage.response.autoConfirmUser = requiresConfirmation != undefined ? !requiresConfirmation : false

    if (email && (requiresConfirmation || requiresConfirmation == undefined)) {
      rawMessage.response.autoVerifyEmail = false
    } else if (email && !requiresConfirmation) {
      rawMessage.response.autoVerifyEmail = true
    }

    if (phoneNumber && (requiresConfirmation || requiresConfirmation == undefined)) {
      rawMessage.response.autoVerifyPhone = false
    } else if (phoneNumber && !requiresConfirmation) {
      rawMessage.response.autoVerifyPhone = true
    }
  }

  return rawMessage
}
