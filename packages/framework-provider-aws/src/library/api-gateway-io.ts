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

export async function graphQLRequestFailed(error: Error): Promise<APIGatewayProxyResult> {
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    statusCode: 200,
    body: JSON.stringify({
      errors: [
        {
          message: error.message,
          locations: [], // TODO: Add locations on GraphQL errors
        },
      ],
    }),
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export async function requestSucceeded(body?: any): Promise<APIGatewayProxyResult> {
  return {
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    statusCode: 200,
    body: body ? JSON.stringify(body) : '',
  }
}
