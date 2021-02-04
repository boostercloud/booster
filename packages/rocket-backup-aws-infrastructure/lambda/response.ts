import { APIGatewayProxyResult } from 'aws-lambda'
import { AWSError } from 'aws-sdk'
import { httpStatusCodeFor } from '../../framework-types/src'

export const response = (statusCode: number, data: object): APIGatewayProxyResult => {
  return {
    statusCode,
    body: JSON.stringify(data),
  };
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