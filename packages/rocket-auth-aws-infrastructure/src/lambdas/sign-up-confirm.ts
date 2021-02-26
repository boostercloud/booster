import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    await cognitoService
      .confirmSignUp({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
        ConfirmationCode: params.confirmationCode,
      })
      .promise()
    return okResponse({
      message: `The username: ${params.username} has been confirmed.`,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
