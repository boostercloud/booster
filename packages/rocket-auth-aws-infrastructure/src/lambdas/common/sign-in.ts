import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, response } from '../response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const isCustom = process.env.mode === 'Passwordless'
    const cognitoService = new CognitoIdentityServiceProvider()
    const initAuthResponse = await cognitoService
      .initiateAuth({
        AuthFlow: isCustom ? 'CUSTOM_AUTH' : 'USER_PASSWORD_AUTH',
        ClientId: process.env.userPoolClientId!,
        AuthParameters: {
          USERNAME: params.username,
          PASSWORD: isCustom ? params.username : params.password,
        },
      })
      .promise()

    if (isCustom) {
      return response(200, {
        session: initAuthResponse.Session,
      })
    }
    return response(200, {
      accessToken: initAuthResponse.AuthenticationResult?.AccessToken,
      idToken: initAuthResponse.AuthenticationResult?.IdToken,
      expiresIn: initAuthResponse.AuthenticationResult?.ExpiresIn,
      refreshToken: initAuthResponse.AuthenticationResult?.RefreshToken,
      tokenType: initAuthResponse.AuthenticationResult?.TokenType,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
