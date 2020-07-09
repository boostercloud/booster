import { APIGatewayProxyResult } from 'aws-lambda'
import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'

export async function requestFailed(error: Error): Promise<APIGatewayProxyResult> {
  const statusCode = httpStatusCodeFor(error)
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    statusCode,
    body: JSON.stringify({
      statusCode,
      title: toClassTitle(error),
      reason: error.message,
    }),
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function requestSucceeded(body?: any, headers?: Record<string, string>): Promise<APIGatewayProxyResult> {
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
      ...headers,
    },
    statusCode: 200,
    body: body ? JSON.stringify(body) : '',
  }
}
