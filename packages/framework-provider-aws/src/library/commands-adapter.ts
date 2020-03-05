import { APIGatewayProxyEvent } from 'aws-lambda'
import { BoosterConfig, CommandEnvelope, Logger, InvalidParameterError } from '@boostercloud/framework-types'
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

export async function submitCommands(
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _commandEnvelopes: Array<CommandEnvelope>,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _config: BoosterConfig,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _logger: Logger
): Promise<void> {
  /* TODO: This method should be implemented once we refactor AWS module to be able to
   * process commands asynchronously. Right now all commands emmited from event handlers or
   * commands are executed synchronously by the `core` module.
   */
}
