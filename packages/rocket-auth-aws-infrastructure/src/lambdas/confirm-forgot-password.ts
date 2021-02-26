import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    await cognitoService
      .confirmForgotPassword({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
        ConfirmationCode: params.confirmationCode,
        Password: params.password,
      })
      .promise()
    return okResponse({
      message: 'Your password has been successfully changed.',
    })
  } catch (e) {
    return errorResponse(e)
  }
}
