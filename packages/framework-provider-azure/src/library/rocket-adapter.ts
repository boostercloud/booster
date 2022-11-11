import { BoosterConfig, RocketEnvelope, rocketFunctionIDEnvVar } from "@boostercloud/framework-types";

export function rawRocketInputToEnvelope(config: BoosterConfig, request: unknown): RocketEnvelope {
  return {
    rocketId: process.env[rocketFunctionIDEnvVar],
  }
}
