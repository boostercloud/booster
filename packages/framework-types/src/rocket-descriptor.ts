import { BoosterConfig } from './config'

export const rocketFunctionIDEnvVar = 'BOOSTER_ROCKET_FUNCTION_ID'

export type RocketFunction = (config: BoosterConfig, request: unknown) => Promise<unknown>

export type RocketDescriptor = {
  packageName: string
  parameters: unknown
}
