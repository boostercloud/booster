import { ScheduledCommandEnvelope, UUID, Logger } from '@boostercloud/framework-types'

export async function rawScheduledInputToEnvelope(
  input: Partial<ScheduledCommandEnvelope>,
  logger: Logger
): Promise<ScheduledCommandEnvelope> {
  logger.debug('Received ScheduledCommand request: ', input)

  if (!input.typeName)
    throw new Error(
      `TypeName is not defined, scheduled command envelope should have the structure {typeName: string}, but you gave ${input}`
    )

  return {
    requestID: UUID.generate(),
    typeName: input.typeName,
  }
}
