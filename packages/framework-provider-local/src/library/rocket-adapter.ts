import { BoosterConfig, RocketEnvelope, rocketFunctionIDEnvVar } from '@boostercloud/framework-types'

export function rawRocketInputToEnvelope(config: BoosterConfig, request: unknown): RocketEnvelope {
  const id = (request as any)[rocketFunctionIDEnvVar]
  return {
    rocketId: id,
  }
}
