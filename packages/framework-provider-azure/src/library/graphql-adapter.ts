import { BoosterConfig, GraphQLRequestEnvelope, GraphQLRequestEnvelopeError } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { Context } from '@azure/functions'

export async function rawGraphQLRequestToEnvelope(
  config: BoosterConfig,
  context: Context
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const logger = getLogger(config, 'graphql-adapter#rawGraphQLRequestToEnvelope')
  logger.debug('Received GraphQL request: ', context.req)
  const requestID = context.executionContext.invocationId
  const connectionContext = context.bindingData?.connectionContext
  const connectionID = connectionContext?.connectionId
  const eventType = connectionContext?.eventType === undefined ? 'MESSAGE' : connectionContext?.eventName?.toUpperCase()

  try {
    let graphQLValue = undefined
    if (context.bindings?.data || context.req?.body) {
      graphQLValue = context.bindings?.data || context.req?.body
    }

    return {
      requestID,
      connectionID,
      eventType,
      token: context.req?.headers?.authorization,
      value: graphQLValue,
      context: {
        request: {
          headers: context.req?.headers,
          body: context.req?.body,
        },
        rawContext: context,
      },
    }
  } catch (e) {
    const error = e as Error
    return {
      error,
      requestID,
      connectionID,
      eventType,
      context: {
        request: {
          headers: context.req?.headers,
          body: context.req?.body,
        },
        rawContext: context,
      },
    }
  }
}
