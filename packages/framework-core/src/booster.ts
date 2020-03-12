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
  private static readonly config = new BoosterConfig()

  /**
   * Avoid creating instances of this class
   */
  private constructor() {}

  public static configureCurrentEnv(configurator: (config: BoosterConfig) => void): void {
    if (!process.env.BOOSTER_ENV) {
      throw new Error('Attempted to configure the current environment, but none was set.')
    }
    this.configure(process.env.BOOSTER_ENV, configurator)
  }

  /**
   * Allows to configure the Booster project.
   *
   * @param configurator A function that receives the configuration object to set the values
   */
  public static configure(environment: string, configurator: (config: BoosterConfig) => void): void {
    if (process.env.BOOSTER_ENV === environment) {
      configurator(this.config)
    }
  }

  /**
   * Initializes the Booster project
   */
  public static start(): void {
    this.logger = buildLogger(this.config.logLevel)
    Importer.importUserProjectFiles()
    this.config.validate()
  }

  /**
   * Entry point to dispatch a command to the corresponding handler and process its result
   */
  public static dispatchCommand(rawCommand: any): Promise<any> {
    return BoosterCommandDispatcher.dispatch(rawCommand, this.config, this.logger)
  }

  /**
   * Entry point to fetch entities
   */
  public static fetchReadModels(readModelsRequest: any): Promise<any> {
    return BoosterReadModelFetcher.fetch(readModelsRequest, this.config, this.logger)
  }

  /**
   * Entry point to validate users upon sign up
   */
  public static checkSignUp(signUpRequest: any): Promise<any> {
    return BoosterAuth.checkSignUp(signUpRequest, this.config, this.logger)
  }

  /**
   * Dispatches event messages to your application.
   */
  public static dispatchEvent(rawEvent: any): Promise<any> {
    return BoosterEventDispatcher.dispatch(rawEvent, this.config, this.logger)
  }

  /**
   * Fetches the last known version of an entity
   * @param entityName Name of the entity class
   * @param entityID
   */
  public static fetchEntitySnapshot(entityName: string, entityID: UUID): Promise<EntityInterface | null> {
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
