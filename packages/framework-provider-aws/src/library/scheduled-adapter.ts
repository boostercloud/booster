import { BoosterConfig, ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export async function rawScheduledInputToEnvelope(
  config: BoosterConfig,
  input: Partial<ScheduledCommandEnvelope>
): Promise<ScheduledCommandEnvelope> {
  const logger = getLogger(config, 'scheduled-adapter#rawScheduledInputToEnvelope')
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
