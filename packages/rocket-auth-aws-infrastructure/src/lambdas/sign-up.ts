import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const isCustom = process.env.mode === 'Passwordless'
    const cognitoService = new CognitoIdentityServiceProvider()
    const signUpResponse = await cognitoService
      .signUp({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
        Password: isCustom ? params.username : params.password,
        UserAttributes: [
          {
            Name: 'custom:role',
            Value: params.userAttributes.role,
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
