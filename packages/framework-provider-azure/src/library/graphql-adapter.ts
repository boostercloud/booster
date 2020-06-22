import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'
import { Context } from '@azure/functions'

export async function rawGraphQLRequestToEnvelope(context: Context, logger: Logger): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', context.req)
  let graphQLBody = undefined
  let graphQLVariables = undefined
  if (context.req) {
    graphQLBody = context.req.body.query
    graphQLVariables = context.req.body.variables
  }

  return {
    requestID: context.executionContext.invocationId,
    eventType: 'MESSAGE',
    value: graphQLBody,
    variables: graphQLVariables,
  }
}
