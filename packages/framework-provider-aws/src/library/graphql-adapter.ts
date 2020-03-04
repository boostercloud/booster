import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent } from 'aws-lambda'
import { fetchUserFromRequest } from './user-envelopes'
import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'

export async function rawGraphQLRequestToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent,
  logger: Logger
): Promise<GraphQLRequestEnvelope> {
  logger.debug('REQUEST:', request)
  return {
    requestID: request.requestContext.requestId,
    eventType: request.requestContext.eventType as GraphQLRequestEnvelope['eventType'],
    connectionID: request.requestContext.connectionId as string,
    currentUser: await fetchUserFromRequest(request, userPool),
    value: request.body ?? undefined,
  }
}
