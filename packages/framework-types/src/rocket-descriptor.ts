import { BoosterConfig } from './config'

export const rocketFunctionIDEnvVar = 'booster-rocket-function-id'

export type RocketFunction = (config: BoosterConfig, request: unknown) => Promise<unknown>

export type RocketDescriptor = {
  packageName: string
  parameters: unknown
}
