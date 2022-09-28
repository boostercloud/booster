import { BoosterConfig } from '@boostercloud/framework-types'

export class BoosterRocketDispatcher {
  constructor(readonly config: BoosterConfig) {}

  public dispatch(request: unknown): Promise<unknown> {
    const rawToEnvelopes = this.config.provider.rockets.rawToEnvelopes(this.config, request)
    const rocketId = rawToEnvelopes.rocketId
    if (!rocketId) {
      throw new Error(
        `Attempt to execute a rocket function but the ID is missing.`
      )
    }
    const rocketFunction = this.config.getRegisteredRocketFunction(rocketId)
    if (!rocketFunction) {
      throw new Error(
        `Rocket function with ID "${rocketId}" not found. Did you forget registering the function with "config.registerRocketFunction()"?`
      )
    }
    return rocketFunction(this.config, request)
  }
}
