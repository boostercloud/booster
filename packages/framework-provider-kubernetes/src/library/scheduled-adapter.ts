/* eslint-disable @typescript-eslint/no-unused-vars */
import { BoosterConfig, ScheduledCommandEnvelope } from '@boostercloud/framework-types'

export const rawToEnvelope = (_config: BoosterConfig, _rawMessage: unknown): Promise<ScheduledCommandEnvelope> => {
  throw new Error('scheduledAdapter#rawToEnvelope: Not implemented')
}
