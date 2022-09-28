import { BoosterConfig, RocketEnvelope, rocketFunctionIDEnvVar } from '@boostercloud/framework-types'

export function rawRocketInputToEnvelope(config: BoosterConfig, request: unknown): RocketEnvelope {
  const id = process.env[rocketFunctionIDEnvVar]
  return {
    rocketId: id,
  }
}
