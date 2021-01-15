import { APIGatewayProxyResult } from 'aws-lambda'
import { AWSError } from 'aws-sdk'

export const response = (statusCode: number, data: object): APIGatewayProxyResult => {
  return {
    statusCode,
    body: JSON.stringify(data),
  }
}

export const errorResponse = (e: AWSError): APIGatewayProxyResult => {
  return response(e.statusCode || 500, {
    error: {
      type: e.code,
      message: e.message,
    },
  })
}
