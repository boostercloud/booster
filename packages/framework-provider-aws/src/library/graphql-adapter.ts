import { APIGatewayProxyWithLambdaAuthorizerEvent } from 'aws-lambda'
import { GraphQLRequestEnvelope, Logger } from '@boostercloud/framework-types'
import { AuthorizerWithUserData } from './auth-adapter'

export async function rawGraphQLRequestToEnvelope(
  request: APIGatewayProxyWithLambdaAuthorizerEvent<AuthorizerWithUserData>,
  logger: Logger
): Promise<GraphQLRequestEnvelope> {
  logger.debug('Received GraphQL request: ', request)
  let graphQLBody = undefined
  let graphQLVariables = undefined
  if (request.body) {
    graphQLBody = JSON.parse(request.body)['query']
    graphQLVariables = JSON.parse(request.body)['variables']
  }

  return {
    requestID: request.requestContext.requestId,
    eventType: (request.requestContext.eventType as GraphQLRequestEnvelope['eventType']) ?? 'MESSAGE',
    connectionID: request.requestContext.connectionId,
    currentUser: JSON.parse(request.requestContext.authorizer.userJSON),
    value: graphQLBody,
    variables: graphQLVariables,
  }
}
