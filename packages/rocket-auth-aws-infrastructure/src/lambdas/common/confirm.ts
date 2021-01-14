import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, response } from '../response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    await cognitoService
      .confirmSignUp({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
        ConfirmationCode: params.code,
      })
      .promise()
    return response(200, {
      message: `Username: ${params.username} confirmed.`,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
