/* eslint-disable @typescript-eslint/no-explicit-any */
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse } from './response'
import { InvalidParameterError } from '@boostercloud/framework-types'
import * as validator from 'validator'

const validateParams = (params: any, isCustomAuth: boolean): void => {
  const rolesConfig = JSON.parse(process.env.rolesConfig!)
  const isRoleProvided = params.userAttributes && params.userAttributes.role && Object.keys(rolesConfig).length > 0
  if (!isRoleProvided) {
    throw new InvalidParameterError('Role not provided')
  }
  const roleMetadata = rolesConfig[params.userAttributes.role]
  if (!roleMetadata) {
    throw new InvalidParameterError(`Unknown role ${params.userAttributes.role}`)
  }

  const authMetadata = roleMetadata.auth
  if (!authMetadata?.signUpMethods?.length) {
    throw new InvalidParameterError(
      `User with role ${params.userAttributes.role} can't sign up by themselves. Choose a different role or contact and administrator`
    )
  }

  const signUpOptions = authMetadata.signUpMethods

  if (isCustomAuth && validator.default.isEmail(params.username)) {
    throw new InvalidParameterError(
      `User with role ${params.userAttributes.role} can't sign up with an email, a phone number is expected`
    )
  }

  if (validator.default.isEmail(params.username) && !signUpOptions.includes('email')) {
    throw new InvalidParameterError(
      `User with role ${params.userAttributes.role} can't sign up with an email, a phone number is expected`
    )
  }

  if (!validator.default.isEmail(params.username) && !signUpOptions.includes('phone')) {
    throw new InvalidParameterError(
      `User with role ${params.username} can't sign up with a phone number, an email is expected`
    )
  }
}

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const isCustomAuth = process.env.mode === 'Passwordless'

    validateParams(params, isCustomAuth)

    const cognitoService = new CognitoIdentityServiceProvider()
    const signUpResponse = await cognitoService
      .signUp({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
        Password: isCustomAuth ? params.username : params.password,
        UserAttributes: [
          {
            Name: 'custom:role',
            Value: params.userAttributes.role ?? '',
          },
        ],
      })
      .promise()
    return okResponse({
      id: signUpResponse.UserSub,
      username: params.username,
      userAttributes: params.userAttributes,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
