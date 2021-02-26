import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse, tokenResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const isCustomAuth = process.env.mode === 'Passwordless'
    const cognitoService = new CognitoIdentityServiceProvider()
    const initAuthResponse = await cognitoService
      .initiateAuth({
        AuthFlow: isCustomAuth ? 'CUSTOM_AUTH' : 'USER_PASSWORD_AUTH',
        ClientId: process.env.userPoolClientId!,
        AuthParameters: {
          USERNAME: params.username,
          PASSWORD: isCustomAuth ? params.username : params.password,
        },
      })
      .promise()

    if (isCustomAuth) {
      return okResponse({
        session: initAuthResponse.Session,
        message: 'Use the session and the code we have sent you via SMS to get your access tokens via POST /token.',
      })
    }
    return tokenResponse(initAuthResponse.AuthenticationResult)
  } catch (e) {
    return errorResponse(e)
  }
}
