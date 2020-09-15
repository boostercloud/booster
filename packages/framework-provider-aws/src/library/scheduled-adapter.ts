import { ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'
import { Logger } from '@boostercloud/framework-types'

export async function rawScheduledInputToEnvelope(
  input: Partial<ScheduledCommandEnvelope>,
  logger: Logger
): Promise<ScheduledCommandEnvelope> {
  logger.debug('Received ScheduledCommand request: ', input)

  if (!input.typeName) throw new Error('TypeName is not defined')

  return {
    requestID: UUID.generate(),
    typeName: input.typeName,
  }
}
