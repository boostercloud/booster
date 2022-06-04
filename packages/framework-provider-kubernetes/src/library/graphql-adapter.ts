/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  BoosterConfig,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  GraphQLClientMessage,
  GraphQLOperation,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export const rawToEnvelope = async (
  config: BoosterConfig,
  request: { body?: GraphQLOperation | GraphQLClientMessage }
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> => {
  const logger = getLogger(config, 'graphql-adapter#rawToEnvelope')
  logger.debug('Received GraphQL request: ', request)
  return {
    requestID: UUID.generate(),
    eventType: 'MESSAGE',
    currentUser: JSON.parse('{}'),
    value: request.body,
  }
}

export const handleResult = async (
  result?: unknown,
  headers?: Record<string, string> | undefined
): Promise<unknown> => ({
  headers: {
    'Access-Control-Allow-Origin': '*',
    ...headers,
  },
  statusCode: 200,
  body: result,
})
