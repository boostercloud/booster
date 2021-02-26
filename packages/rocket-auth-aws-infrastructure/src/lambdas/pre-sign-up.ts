import { CognitoUserPoolEvent } from 'aws-lambda'

export const handler = async (event: CognitoUserPoolEvent): Promise<CognitoUserPoolEvent> => {
  const { 'custom:role': role } = event.request.userAttributes
  const rolesConfig = JSON.parse(process.env.rolesConfig!)

  let skipConfirmation = false
  const isRoleProvided = role && rolesConfig[role] && Object.keys(rolesConfig).length > 0

  if (isRoleProvided) {
    skipConfirmation = rolesConfig[role].auth?.skipConfirmation ?? false
  }

  event.response.autoConfirmUser = skipConfirmation
  return event
}
