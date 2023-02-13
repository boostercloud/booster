/* eslint-disable @typescript-eslint/no-unused-vars */
import { BoosterConfig, RocketEnvelope } from '@boostercloud/framework-types'

export const rawRocketInputToEnvelope = (_config: BoosterConfig, _rawMessage: unknown): RocketEnvelope => {
  throw new Error('rocketAdapter#rawToEnvelope: Not implemented')
}
