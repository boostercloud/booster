import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, tokenResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    const initAuthResponse = await cognitoService
      .initiateAuth({
        AuthFlow: 'REFRESH_TOKEN_AUTH',
        ClientId: process.env.userPoolClientId!,
        AuthParameters: {
          REFRESH_TOKEN: params.refreshToken,
        },
      })
      .promise()
    return tokenResponse(initAuthResponse.AuthenticationResult)
  } catch (e) {
    return errorResponse(e)
  }
}
