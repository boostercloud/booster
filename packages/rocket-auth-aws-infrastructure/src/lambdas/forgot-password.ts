import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    await cognitoService
      .forgotPassword({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
      })
      .promise()
    return okResponse({
      message: `The confirmation code to change your password has been sent to: ${params.username}.`,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
