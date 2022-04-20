import { Logger, ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'

interface LocalScheduleCommandEnvelope {
  typeName: string
}

export async function rawScheduledInputToEnvelope(
  input: Partial<LocalScheduleCommandEnvelope>,
  logger: Logger
): Promise<ScheduledCommandEnvelope> {
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
