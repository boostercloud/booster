import {
  BoosterConfig,
  Logger,
  EntityInterface,
  ReadModelInterface,
  UUID,
  Class,
  Searcher,
} from '@boostercloud/framework-types'
import { Importer } from './importer'
import { buildLogger } from './booster-logger'
import { fetchEntitySnapshot } from './entity-snapshot-fetcher'

/**
 * Static (singleton) class that provides access to the logger, the config,
 * and the runtime methods to configure, manage and interact with Booster features.
 */
export class BoosterApp {
  /**
   * Set of environment names set from the application using `Booster.configure('environment_name', ...)`
   */
  public static readonly configuredEnvironments: Set<string> = new Set<string>()

  /**
   * Default logger
   */
  public static logger: Logger
  public static readonly config = new BoosterConfig(checkAndGetCurrentEnv())

  /**
   * Forbid creating `BoosterApp` instances
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
    this.logger = buildLogger(this.config.logLevel)
    Importer.importUserProjectFiles(codeRootPath)
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

  /**
   * Fetches the last known version of an entity
   * @param entityName Name of the entity class
   * @param entityID
   */
  public static fetchEntitySnapshot<TEntity extends EntityInterface>(
    entityClass: Class<TEntity>,
    entityID: UUID
  ): Promise<TEntity | undefined> {
    return fetchEntitySnapshot(this.config, this.logger, entityClass, entityID)
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
