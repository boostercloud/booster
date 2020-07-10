import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'
import { Context } from '@azure/functions'

export async function rawGraphQLRequestToEnvelope(context: Context, logger: Logger): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', context.req)
  let graphQLValue = undefined
  if (context.req) {
    graphQLValue = context.req.body
  }

  return {
    requestID: context.executionContext.invocationId,
    eventType: 'MESSAGE',
    value: graphQLValue,
  }
}
