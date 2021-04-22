import {
  BoosterConfig,
  Logger,
  EntityInterface,
  ReadModelInterface,
  UUID,
  Class,
  Searcher,
  EventSearchResponse,
  EventFilter,
} from '@boostercloud/framework-types'
import { Importer } from './importer'
import { buildLogger } from './booster-logger'
import { BoosterEventDispatcher } from './booster-event-dispatcher'
import { BoosterAuth } from './booster-auth'
import { fetchEntitySnapshot } from './entity-snapshot-fetcher'
import { BoosterGraphQLDispatcher } from './booster-graphql-dispatcher'
import { BoosterSubscribersNotifier } from './booster-subscribers-notifier'
import { BoosterScheduledCommandDispatcher } from './booster-scheduled-command-dispatcher'

/**
 * Main class to interact with Booster and configure it.
 * Sensible defaults are used whenever possible:
 * - `provider`: `Provider.AWS`
 * - `appName`: `new-booster-app`
 * - `region`: 'eu-west-1'
 *
 */
export class Booster {
  public static readonly configuredEnvironments: Set<string> = new Set<string>()
  private static logger: Logger
  public static readonly config = new BoosterConfig(checkAndGetCurrentEnv())
  /**
   * Avoid creating instances of this class
   */
  private constructor() {}

  public static configureCurrentEnv(configurator: (config: BoosterConfig) => void): void {
    configurator(this.config)
  }

  /**
   * Allows to configure the Booster project.
   *
   * @param environment The name of the environment you want to configure
   * @param configurator A function that receives the configuration object to set the values
   */
  public static configure(environment: string, configurator: (config: BoosterConfig) => void): void {
    this.configuredEnvironments.add(environment)
    if (this.config.environmentName === environment) {
      configurator(this.config)
    }
  }

  /**
   * Initializes the Booster project
   */
  public static start(codeRootPath: string): void {
    const projectRootPath = codeRootPath.replace(new RegExp(this.config.codeRelativePath + '$'), '')
    this.config.userProjectRootPath = projectRootPath
    Importer.importUserProjectFiles(codeRootPath)
    this.logger = buildLogger(this.config.logLevel)
    this.config.validate()
  }

  /**
   * This function returns a "Searcher" configured to search instances of the read model class passed as param.
   * For more information, check the Searcher class.
   * @param readModelClass The class of the read model you what to run searches for
   */
  public static readModel<TReadModel extends ReadModelInterface>(
    readModelClass: Class<TReadModel>
  ): Searcher<TReadModel> {
    const searchFunction = this.config.provider.readModels.search.bind(null, this.config, this.logger)
    return new Searcher(readModelClass, searchFunction)
  }

  public static async events(filters: EventFilter): Promise<Array<EventSearchResponse>> {
    return this.config.provider.events.search(this.config, this.logger, filters)
  }
  /**
   * Entry point to validate users upon sign up
   */
  public static async checkSignUp(signUpRequest: unknown): Promise<unknown> {
    return BoosterAuth.checkSignUp(signUpRequest, this.config, this.logger)
  }

  /**
   * Dispatches event messages to your application.
   */
  public static dispatchEvent(rawEvent: unknown): Promise<unknown> {
    return BoosterEventDispatcher.dispatch(rawEvent, this.config, this.logger)
  }

  public static serveGraphQL(request: unknown): Promise<unknown> {
    return new BoosterGraphQLDispatcher(this.config, this.logger).dispatch(request)
  }

  public static triggerScheduledCommand(request: unknown): Promise<unknown> {
    return new BoosterScheduledCommandDispatcher(this.config, this.logger).dispatch(request)
  }

  public static notifySubscribers(request: unknown): Promise<unknown> {
    return new BoosterSubscribersNotifier(this.config, this.logger).dispatch(request)
  }

  /**
   * Fetches the last known version of an entity
   * @param entityClass Name of the entity class
   * @param entityID
   */
  public static entity<TEntity extends EntityInterface>(
    entityClass: Class<TEntity>,
    entityID: UUID
  ): Promise<TEntity | undefined> {
    return fetchEntitySnapshot(this.config, this.logger, entityClass, entityID)
  }

  /** @deprecated Use method "entity" instead */
  public static fetchEntitySnapshot<TEntity extends EntityInterface>(
    entityClass: Class<TEntity>,
    entityID: UUID
  ): Promise<TEntity | undefined> {
    return this.entity(entityClass, entityID)
  }
}

function checkAndGetCurrentEnv(): string {
  const env = process.env.BOOSTER_ENV
  if (!env || env.trim().length == 0) {
    throw new Error(
      'Booster environment is missing. You need to provide an environment to configure your Booster project'
    )
  }
  return env
}

export async function boosterEventDispatcher(rawEvent: unknown): Promise<unknown> {
  return Booster.dispatchEvent(rawEvent)
}

export async function boosterPreSignUpChecker(rawMessage: unknown): Promise<unknown> {
  return Booster.checkSignUp(rawMessage)
}

export async function boosterServeGraphQL(rawRequest: unknown): Promise<unknown> {
  return Booster.serveGraphQL(rawRequest)
}

export async function boosterTriggerScheduledCommand(rawRequest: unknown): Promise<unknown> {
  return Booster.triggerScheduledCommand(rawRequest)
}

export async function boosterNotifySubscribers(rawRequest: unknown): Promise<unknown> {
  return Booster.notifySubscribers(rawRequest)
}
