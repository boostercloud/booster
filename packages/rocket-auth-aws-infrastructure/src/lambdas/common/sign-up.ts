import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, response } from '../response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  const passwordForCustomFlow = '4dm1N@2021!'
  try {
    const params = JSON.parse(event.body!)
    const isCustom = process.env.mode === 'Passwordless'
    const cognitoService = new CognitoIdentityServiceProvider()
    const signUpResponse = await cognitoService
      .signUp({
        ClientId: process.env.userPoolClientId!,
        Username: params.username,
        Password: isCustom ? passwordForCustomFlow : params.password,
        UserAttributes: [
          {
            Name: 'custom:role',
            Value: params.role,
          },
        ],
      })
      .promise()
    return response(200, {
      userId: signUpResponse.UserSub,
      username: params.username,
    })
  } catch (e) {
    return errorResponse(e)
  }
}
