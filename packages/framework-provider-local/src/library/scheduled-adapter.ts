import { BoosterConfig, ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

interface LocalScheduleCommandEnvelope {
  typeName: string
}

export async function rawScheduledInputToEnvelope(
  config: BoosterConfig,
  input: Partial<LocalScheduleCommandEnvelope>
): Promise<ScheduledCommandEnvelope> {
  const logger = getLogger(config, 'rawScheduledInputToEnvelope')
  logger.debug('Received LocalScheduleCommand request: ', input)

  if (!input.typeName)
    throw new Error(
      `typeName is not defined or empty, scheduled command envelope should have the structure {typeName: string }, but you gave ${JSON.stringify(
        input
      )}`
    )

  return {
    requestID: UUID.generate(),
    typeName: input.typeName,
  }
}
