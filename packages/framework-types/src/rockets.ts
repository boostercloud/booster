import { BoosterConfig } from './config'

export const rocketFunctionIdEnvironmentVariable = 'BOOSTER_ROCKET_FUNCTION_ID'

export type RocketFunction = (config: BoosterConfig, request: unknown) => Promise<unknown>

export interface RocketDescriptor {
  packageName: string
  parameters: unknown
}
