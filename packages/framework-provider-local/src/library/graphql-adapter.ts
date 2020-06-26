import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'

export async function rawGraphQLRequestToEnvelope(request: any, logger: Logger): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)

  let graphQLValue = undefined
  if (request.body) {
    graphQLValue = JSON.parse(request.body)
  }

  return {
    requestID: request.requestContext?.requestId,
    eventType: (request.requestContext?.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
    connectionID: request.requestContext?.connectionId,
    currentUser: {
      email: 'test@test.com',
      roles: [],
    },
    value: graphQLValue,
  }
}
