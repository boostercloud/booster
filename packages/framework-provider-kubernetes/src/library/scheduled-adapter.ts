/* eslint-disable @typescript-eslint/no-unused-vars */
import { Logger, ScheduledCommandEnvelope } from '@boostercloud/framework-types'

export const rawToEnvelope = (_rawMessage: unknown, _logger: Logger): Promise<ScheduledCommandEnvelope> => {
  throw new Error('scheduledAdapter#rawToEnvelope: Not implemented')
}
