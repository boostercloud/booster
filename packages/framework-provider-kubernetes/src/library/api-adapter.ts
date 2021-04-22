/* eslint-disable @typescript-eslint/no-unused-vars */
import * as GraphQLAdapter from './graphql-adapter'
import { httpStatusCodeFor, toClassTitle } from '@boostercloud/framework-types'

export const requestSucceeded = async (_body?: unknown): Promise<unknown> => {
  return GraphQLAdapter.handleResult(_body)
}

export const requestFailed = async (_error: Error): Promise<unknown> => {
  const statusCode = httpStatusCodeFor(_error)
  return {
    headers: {
      'Access-Control-Allow-Origin': '*'
    },
    statusCode,
    body: {
      statusCode,
      title: toClassTitle(_error),
      reason: _error.message,
    },
  }
}
