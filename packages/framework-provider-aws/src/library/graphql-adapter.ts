import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { APIGatewayProxyEvent } from 'aws-lambda'
import { fetchUserFromRequest } from './user-envelopes'
import { GraphQLRequestEnvelope, InvalidParameterError } from '@boostercloud/framework-types'

export async function rawGraphQLRequestToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent
): Promise<GraphQLRequestEnvelope> {
  if (request.body) {
    return {
      requestID: request.requestContext.requestId,
      currentUser: await fetchUserFromRequest(request, userPool),
      value: request.body,
    }
  } else {
    throw new InvalidParameterError('The field "body" from the API Gateway Event arrived empty.')
  }
}
