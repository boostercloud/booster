import { APIGatewayProxyEvent } from 'aws-lambda'
import { CommandEnvelope, InvalidParameterError } from '@boostercloud/framework-types'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { fetchUserFromRequest } from './user-envelopes'

export async function rawCommandToEnvelope(
  userPool: CognitoIdentityServiceProvider,
  request: APIGatewayProxyEvent
): Promise<CommandEnvelope> {
  if (request.body) {
    const envelope = JSON.parse(request.body) as CommandEnvelope
    envelope.requestID = request.requestContext.requestId
    envelope.currentUser = await fetchUserFromRequest(request, userPool)
    return envelope
  } else {
    throw new InvalidParameterError('The field "body" from the API Gateway Event arrived empty.')
  }
}
