/* eslint-disable @typescript-eslint/no-unused-vars */
import {
  Logger,
  BoosterConfig,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
  GraphQLClientMessage,
  GraphQLOperation,
  UUID,
} from '@boostercloud/framework-types'

export const rawToEnvelope = async (
  request: { body?: GraphQLOperation | GraphQLClientMessage },
  logger: Logger,
  _config: BoosterConfig
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> => {
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
