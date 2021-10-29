import { Logger, ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'

interface AzureScheduledCommandEnvelope {
  bindings: {
    [name: string]: object
  }
}

export async function rawScheduledInputToEnvelope(
  input: Partial<AzureScheduledCommandEnvelope>,
  logger: Logger
): Promise<ScheduledCommandEnvelope> {
  logger.debug('Received AzureScheduledCommand request: ', input)

  if (!input.bindings || !Object.keys(input.bindings).length)
    throw new Error(
      `bindings is not defined or empty, scheduled command envelope should have the structure {bindings: [name: string]: string }, but you gave ${JSON.stringify(
        input
      )}`
    )

  return {
    requestID: UUID.generate(),
    typeName: Object.keys(input.bindings)[0],
  }
}
