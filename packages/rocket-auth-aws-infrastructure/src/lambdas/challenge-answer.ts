import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, tokenResponse } from './response'

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
          ANSWER: params.confirmationCode,
        },
        Session: params.session,
      })
      .promise()
    return tokenResponse(authChallengeResponse.AuthenticationResult)
  } catch (e) {
    return errorResponse(e)
  }
}
