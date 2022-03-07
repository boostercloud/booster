import { createInstance, createInstances } from '@boostercloud/framework-common-helpers'
import {
  BoosterConfig,
  Class,
  EntityInterface,
  EventSearchRequest,
  EventSearchResponse,
  FilterFor,
  FinderByKeyFunction,
  Logger,
  ReadModelInterface,
  ReadOnlyNonEmptyArray,
  Searcher,
  SearcherFunction,
  SequenceKey,
  UUID,
} from '@boostercloud/framework-types'
import { BoosterEventDispatcher } from './booster-event-dispatcher'
import { BoosterGraphQLDispatcher } from './booster-graphql-dispatcher'
import { buildLogger } from './booster-logger'
import { BoosterScheduledCommandDispatcher } from './booster-scheduled-command-dispatcher'
import { BoosterSubscribersNotifier } from './booster-subscribers-notifier'
import { Importer } from './importer'
import { EventStore } from './services/event-store'
import { BoosterRocketDispatcher } from './booster-rocket-dispatcher'

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
    this.logger = buildLogger(this.config.logLevel, this.config)
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
    const searchFunction: SearcherFunction<TReadModel> = async (
      readModelName: string,
      filters: FilterFor<unknown>,
      limit?: number,
      afterCursor?: any,
      paginatedVersion?: boolean
    ) => {
      const searchResult = await this.config.provider.readModels.search(
        this.config,
        this.logger,
        readModelName,
        filters,
        limit,
        afterCursor,
        paginatedVersion
      )

      if (!Array.isArray(searchResult)) {
        return {
          ...searchResult,
          items: createInstances(readModelClass, searchResult.items),
        }
      }
      return createInstances(readModelClass, searchResult)
    }

    const finderByIdFunction: FinderByKeyFunction<TReadModel> = async (
      readModelName: string,
      id: UUID,
      sequenceKey?: SequenceKey
    ) => {
      const readModels = await this.config.provider.readModels.fetch(
        this.config,
        this.logger,
        readModelName,
        id,
        sequenceKey
      )
      if (sequenceKey) {
        return readModels as ReadOnlyNonEmptyArray<TReadModel>
      }
      return readModels[0] as TReadModel
    }
    return new Searcher(readModelClass, searchFunction, finderByIdFunction)
  }

  public static async events(request: EventSearchRequest): Promise<Array<EventSearchResponse>> {
    const events: Array<EventSearchResponse> = await this.config.provider.events.search(
      this.config,
      this.logger,
      request.filters,
      request.limit
    )
    return events.map((event) => {
      const eventMetadata = this.config.events[event.type]
      event.value = createInstance(eventMetadata.class, event.value)
      return event
    })
  }

  /**
   * Fetches the last known version of an entity
   * @param entityClass Name of the entity class
   * @param entityID
   */
  public static async entity<TEntity extends EntityInterface>(
    entityClass: Class<TEntity>,
    entityID: UUID
  ): Promise<TEntity | undefined> {
    const eventStore = new EventStore(this.config, this.logger)
    const entitySnapshotEnvelope = await eventStore.fetchEntitySnapshot(entityClass.name, entityID)
    return entitySnapshotEnvelope ? createInstance(entityClass, entitySnapshotEnvelope.value) : undefined
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

  public static dispatchRocket(request: unknown): Promise<unknown> {
    return new BoosterRocketDispatcher(this.config, this.logger).dispatch(request)
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

export async function boosterServeGraphQL(rawRequest: unknown): Promise<unknown> {
  return Booster.serveGraphQL(rawRequest)
}

export async function boosterTriggerScheduledCommand(rawRequest: unknown): Promise<unknown> {
  return Booster.triggerScheduledCommand(rawRequest)
}

export async function boosterNotifySubscribers(rawRequest: unknown): Promise<unknown> {
  return Booster.notifySubscribers(rawRequest)
}

export async function boosterRocketDispatcher(rawRequest: unknown): Promise<unknown> {
  return Booster.dispatchRocket(rawRequest)
}
