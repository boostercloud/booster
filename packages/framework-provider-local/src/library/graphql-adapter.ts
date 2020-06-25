import { Logger } from '@boostercloud/framework-types'
import { GraphQLRequestEnvelope } from '@boostercloud/framework-types/dist'

export async function rawGraphQLRequestToEnvelope(request: any, logger: Logger): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)
  let graphQLBody = undefined
  let graphQLVariables = undefined

  if (request.body) {
    graphQLBody = request.body?.query
    graphQLVariables = request.body?.variables
  }

  return {
    requestID: request.requestContext?.requestId,
    eventType: (request.requestContext?.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
    connectionID: request.requestContext?.connectionId,
    currentUser: {
      email: 'test@test.com',
      roles: [],
    },
    value: graphQLBody,
    variables: graphQLVariables,
  }
}
