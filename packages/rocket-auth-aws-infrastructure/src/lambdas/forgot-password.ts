import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, response } from './response'

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
    return response(200, {
      message: "We have sen't you a confirmation code to change your password.",
    })
  } catch (e) {
    return errorResponse(e)
  }
}
