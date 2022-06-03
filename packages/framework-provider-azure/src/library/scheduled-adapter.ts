import { BoosterConfig, ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

interface AzureScheduledCommandEnvelope {
  bindings: {
    [name: string]: unknown
  }
}

export async function rawScheduledInputToEnvelope(
  config: BoosterConfig,
  input: Partial<AzureScheduledCommandEnvelope>
): Promise<ScheduledCommandEnvelope> {
  const logger = getLogger(config, 'scheduled-adapter#rawScheduledInputToEnvelope')
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
