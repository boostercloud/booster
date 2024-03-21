import { BoosterConfig } from './config'

/**
 * `UserApp` represents the expected interface when we `require` the `dist/index.js` file of a Booster app
 */
export interface UserApp {
  Booster: {
    config: BoosterConfig
    configureCurrentEnv(configurator: (config: BoosterConfig) => void): void
    configuredEnvironments: Set<string>
  }
  boosterEventDispatcher(_: unknown): Promise<void>
  boosterServeGraphQL(_: unknown): Promise<unknown>
  boosterTriggerScheduledCommands(_: unknown): Promise<void>
  boosterNotifySubscribers(_: unknown): Promise<void>
  boosterRocketDispatcher(_: unknown): Promise<unknown>
  boosterConsumeEventStream(_: unknown): Promise<unknown>
  boosterProduceEventStream(_: unknown): Promise<unknown>
  boosterHealth(_: unknown): Promise<any>
}
