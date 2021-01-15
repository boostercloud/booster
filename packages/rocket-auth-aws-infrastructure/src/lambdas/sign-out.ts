import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent, APIGatewayProxyResult } from 'aws-lambda'
import { errorResponse, okResponse } from './response'

export const handler = async (event: APIGatewayProxyEvent): Promise<APIGatewayProxyResult> => {
  try {
    const params = JSON.parse(event.body!)
    const cognitoService = new CognitoIdentityServiceProvider()
    await cognitoService
      .globalSignOut({
        AccessToken: params.accessToken,
      })
      .promise()
    return okResponse({
      message: 'Signed out',
    })
  } catch (e) {
    return errorResponse(e)
  }
}
