import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, response } from '../response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    const authChallengeResponse = await cognitoService
      .respondToAuthChallenge({
        ChallengeName: 'CUSTOM_CHALLENGE',
        ClientId: process.env.userPoolClientId!,
        ChallengeResponses: {
          USERNAME: params.username,
          ANSWER: params.code,
        },
        Session: params.session,
      })
      .promise()

    return response(200, {
      accessToken: authChallengeResponse.AuthenticationResult?.AccessToken,
      idToken: authChallengeResponse.AuthenticationResult?.IdToken,
      expiresIn: authChallengeResponse.AuthenticationResult?.ExpiresIn,
      refreshToken: authChallengeResponse.AuthenticationResult?.RefreshToken,
      tokenType: authChallengeResponse.AuthenticationResult?.TokenType,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
