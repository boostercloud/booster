import { APIGatewayProxyResult } from 'aws-lambda'

export function fail(statusCode: number, title: string, reason: string): APIGatewayProxyResult {
  return {
    statusCode: statusCode,
    body: JSON.stringify({
      statusCode,
      title,
      reason,
    }),
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function succeed(body?: any): APIGatewayProxyResult {
  return {
    statusCode: 200,
    body: body ? JSON.stringify(body) : '',
  }
}
