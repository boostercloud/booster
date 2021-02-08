import { APIGatewayProxyResult } from 'aws-lambda'
import { AWSError, CognitoIdentityServiceProvider } from 'aws-sdk'
import { httpStatusCodeFor } from '@boostercloud/framework-types'

const headers = {
  ['Access-Control-Allow-Headers']: '*',
  ['Access-Control-Allow-Methods']: 'GET,POST,PUT,DELETE,OPTIONS,PATCH',
  ['Access-Control-Allow-Origin']: '*',
}

export const response = (statusCode: number, data: object): APIGatewayProxyResult => {
  return {
    headers,
    statusCode,
    body: JSON.stringify(data),
  }
}

export const errorResponse = (e: AWSError): APIGatewayProxyResult => {
  return response(httpStatusCodeFor(e), {
    error: {
      type: e.code,
      message: e.message,
    },
  })
}

export const okResponse = (data: object): APIGatewayProxyResult => {
  return response(200, data)
}

export const tokenResponse = (
  authResult?: CognitoIdentityServiceProvider.AuthenticationResultType
): APIGatewayProxyResult => {
  return response(200, {
    accessToken: authResult?.AccessToken || '',
    idToken: authResult?.IdToken || '',
    expiresIn: authResult?.ExpiresIn || 0,
    refreshToken: authResult?.RefreshToken || '',
    tokenType: authResult?.TokenType || '',
  })
}
