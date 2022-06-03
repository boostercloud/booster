import { BoosterConfig, rocketFunctionIDEnvVar } from '@boostercloud/framework-types'

export class BoosterRocketDispatcher {
  constructor(readonly config: BoosterConfig) {}

  public dispatch(request: unknown): Promise<unknown> {
    const rocketFunctionID = process.env[rocketFunctionIDEnvVar]
    if (!rocketFunctionID) {
      throw new Error(
        `Attempt to execute a rocket function but the ID is missing. Did you forget to set the ID in the environment variable "${rocketFunctionIDEnvVar}"?`
      )
    }
    const rocketFunction = this.config.getRegisteredRocketFunction(rocketFunctionID)
    if (!rocketFunction) {
      throw new Error(
        `Rocket function with ID "${rocketFunctionID}" not found. Did you forget registering the function with "config.registerRocketFunction()"?`
      )
    }
    return rocketFunction(this.config, request)
  }
}
