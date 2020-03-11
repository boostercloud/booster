/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Importer } from './importer'
import { buildLogger } from './booster-logger'
import { BoosterCommandDispatcher } from './booster-command-dispatcher'
import { BoosterReadModelFetcher } from './booster-read-model-fetcher'
import { BoosterEventDispatcher } from './booster-event-dispatcher'
import { BoosterAuth } from './booster-auth'
import { EntityInterface, UUID } from '@boostercloud/framework-types'
import { fetchEntitySnapshot } from './entity-snapshot-fetcher'

/**
 * Main class to interact with Booster and configure it.
 * Sensible defaults are used whenever possible:
 * - `provider`: `Provider.AWS`
 * - `appName`: `new-booster-app`
 * - `region`: 'eu-west-1'
 */
export class Booster {
  private static logger: Logger

  /**
   * Active Booster configuration
   */
  private static config?: BoosterConfig

  private static readonly environments: Record<string, BoosterConfig> = {}

  /**
   * Avoid creating instances of this class
   */
  private constructor() {}

  /**
   * Allows to specify environments in the Booster project.
   *
   * @param environmentName A name for the environment that will use the configuration
   * @param configurator A function that receives the configuration object to set the values
   */
  public static environment(environmentName: string, configurator: (config: BoosterConfig) => void): void {
    const newConfig = new BoosterConfig()
    configurator(newConfig)
    this.environments[environmentName] = newConfig
  }

  public static configure(configurator: (config: BoosterConfig) => void): void {
    this.assertConfigIsSelected(this.config)
    configurator(this.config)
  }
  /**
   * Initializes the Booster project
   */
  public static start(): void {
    if (!this.config) {
      Booster.selectEnvironment()
      this.assertConfigIsSelected(this.config)
    }
    this.logger = buildLogger(this.config.logLevel)
    Importer.importUserProjectFiles()
    this.config.validate()
  }

  private static assertConfigIsSelected(config?: BoosterConfig): asserts config is BoosterConfig {
    if (!config) {
      throw new Error(`Attempted to use the selected environment, yet no environment has been selected.

      Please report this as an issue in the Booster repo:
      https://github.com/boostercloud/booster/issues`)
    }
  }

  public static selectEnvironment() {
    const availableEnvironments = Object.keys(this.environments)
    if (availableEnvironments.length === 0) {
      throw new Error('No available environments have been found. They should be configured within `src/config`')
    }
    const selectedEnvironment = process.env.BOOSTER_ENV
    if (!selectedEnvironment) {
      throw new Error(`BOOSTER_ENV environment variable has not been set.
      In order for Booster to work, you must provide which environment you want to use.

      Available environments are:
      * ${availableEnvironments.join('\n* ')}`)
    }
    if (!availableEnvironments.includes(selectedEnvironment)) {
      throw new Error(`No available environments named ${selectedEnvironment} have been found.

      Available environments are:
      * ${availableEnvironments.join('\n* ')}`)
    }
    this.config = this.environments[selectedEnvironment]
  }

  /**
   * Entry point to dispatch a command to the corresponding handler and process its result
   */
  public static dispatchCommand(rawCommand: any): Promise<any> {
    Booster.selectEnvironment()
    this.assertConfigIsSelected(this.config)
    return BoosterCommandDispatcher.dispatch(rawCommand, this.config, this.logger)
  }

  /**
   * Entry point to fetch entities
   */
  public static fetchReadModels(readModelsRequest: any): Promise<any> {
    Booster.selectEnvironment()
    this.assertConfigIsSelected(this.config)
    return BoosterReadModelFetcher.fetch(readModelsRequest, this.config, this.logger)
  }

  /**
   * Entry point to validate users upon sign up
   */
  public static checkSignUp(signUpRequest: any): Promise<any> {
    Booster.selectEnvironment()
    this.assertConfigIsSelected(this.config)
    return BoosterAuth.checkSignUp(signUpRequest, this.config, this.logger)
  }

  /**
   * Dispatches event messages to your application.
   */
  public static dispatchEvent(rawEvent: any): Promise<any> {
    Booster.selectEnvironment()
    this.assertConfigIsSelected(this.config)
    return BoosterEventDispatcher.dispatch(rawEvent, this.config, this.logger)
  }

  /**
   * Fetches the last known version of an entity
   * @param entityName Name of the entity class
   * @param entityID
   */
  public static fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EntityInterface | null> {
    Booster.selectEnvironment()
    this.assertConfigIsSelected(this.config)
    return fetchEntitySnapshot(this.config, this.logger, entityName, entityID)
  }
}

export async function boosterCommandDispatcher(rawCommand: any): Promise<any> {
  return Booster.dispatchCommand(rawCommand)
}

export async function boosterEventDispatcher(rawEvent: any): Promise<any> {
  return Booster.dispatchEvent(rawEvent)
}

export async function boosterReadModelMapper(rawMessage: any): Promise<any> {
  return Booster.fetchReadModels(rawMessage)
}

export async function boosterPreSignUpChecker(rawMessage: any): Promise<void> {
  return Booster.checkSignUp(rawMessage)
}
