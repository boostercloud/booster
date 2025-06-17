import { createInstance } from '@boostercloud/framework-common-helpers'
import {
  BoosterConfig,
  BoosterConfigTag,
  Class,
  EntityInterface,
  EventDeleteParameters,
  EventSearchParameters,
  EventSearchResponse,
  PaginatedEntitiesIdsResult,
  ReadModelInterface,
  Searcher,
  UUID,
} from '@boostercloud/framework-types'
import { Importer } from './importer'
import { EventStore } from './services/event-store'
import { BoosterEntityMigrated } from './core-concepts/data-migration/events/booster-entity-migrated'
import { BoosterDataMigrationEntity } from './core-concepts/data-migration/entities/booster-data-migration-entity'
import { BoosterDataMigrationStarted } from './core-concepts/data-migration/events/booster-data-migration-started'
import { BoosterDataMigrationFinished } from './core-concepts/data-migration/events/booster-data-migration-finished'
import { JwksUriTokenVerifier, JWT_ENV_VARS } from './services/token-verifiers'
import { BoosterAuthorizer } from './booster-authorizer'
import { BoosterEntityTouched } from './core-concepts/touch-entity/events/booster-entity-touched'
import { readModelSearcher } from './services/read-model-searcher'
import { BoosterDeleteEventDispatcher } from './booster-delete-event-dispatcher'
import { eventSearch } from './booster-event-search'
import { Effect, pipe } from 'effect'
import { Command } from '@effect/cli'
import * as path from 'path'
import * as Injectable from './injectable'
import { NodeContext, NodeRuntime } from '@effect/platform-node'

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
  public static readonly config = new BoosterConfig(checkAndGetCurrentEnv())

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
    this.configureBoosterConcepts()
    this.loadTokenVerifierFromEnv()
    this.config.validate()
    const args = process.argv
    if (process.env['BOOSTER_CLI_HOOK']?.trim() !== 'true') {
      return
    }
    const injectable = this.config.injectable
    if (injectable) {
      const { commands, runMain, contextProvider } = injectable as Injectable.Injectable
      const provider = contextProvider ?? NodeContext.layer
      const runner = runMain ?? NodeRuntime.runMain
      const name = 'boost'
      const version = require(path.join(projectRootPath, 'package.json')).version
      const command = Command.make('boost').pipe(Command.withSubcommands(commands))
      // Run the generated CLI
      pipe(
        args,
        Command.run(command, {
          name,
          version,
        }),
        // TODO: Improve error messages
        Effect.provide(provider),
        Effect.provideService(BoosterConfigTag, this.config),
        runner
      )
    }
  }

  /**
   * This function returns a "Searcher" configured to search instances of the read model class passed as param.
   * For more information, check the Searcher class.
   * @param readModelClass The class of the read model you what to run searches for
   */
  public static readModel<TReadModel extends ReadModelInterface>(
    readModelClass: Class<TReadModel>
  ): Searcher<TReadModel> {
    return readModelSearcher(this.config, readModelClass)
  }

  public static async events(request: EventSearchParameters): Promise<Array<EventSearchResponse>> {
    return eventSearch(this.config, request)
  }

  public static async entitiesIDs(
    entityTypeName: string,
    limit: number,
    afterCursor?: Record<string, string>
  ): Promise<PaginatedEntitiesIdsResult> {
    return await this.config.provider.events.searchEntitiesIDs(this.config, limit, afterCursor, entityTypeName)
  }

  public static async deleteEvent(parameters: EventDeleteParameters): Promise<boolean> {
    return await BoosterDeleteEventDispatcher.deleteEvent(this.config, parameters)
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
    const eventStore = new EventStore(this.config)
    const entitySnapshotEnvelope = await eventStore.fetchEntitySnapshot(entityClass.name, entityID)
    return entitySnapshotEnvelope ? createInstance(entityClass, entitySnapshotEnvelope.value) : undefined
  }

  private static configureBoosterConcepts(): void {
    this.configureDataMigrations()
    this.configureTouchEntities()
  }

  private static configureDataMigrations(): void {
    this.config.events[BoosterEntityMigrated.name] = {
      class: BoosterEntityMigrated,
    }

    this.config.events[BoosterDataMigrationStarted.name] = {
      class: BoosterDataMigrationStarted,
    }

    this.config.reducers[BoosterDataMigrationStarted.name] = {
      class: BoosterDataMigrationEntity,
      methodName: 'started',
    }

    this.config.events[BoosterDataMigrationFinished.name] = {
      class: BoosterDataMigrationFinished,
    }

    this.config.reducers[BoosterDataMigrationFinished.name] = {
      class: BoosterDataMigrationEntity,
      methodName: 'finished',
    }

    this.config.entities[BoosterDataMigrationEntity.name] = {
      class: BoosterDataMigrationEntity,
      eventStreamAuthorizer: BoosterAuthorizer.denyAccess,
    }
  }

  private static configureTouchEntities(): void {
    this.config.events[BoosterEntityTouched.name] = {
      class: BoosterEntityTouched,
    }
  }

  /**
   * TODO: We're loading tokenVerifier options from environment variables here for backwards
   * compatibility reasons, but the preferred way to initialize the project token verifiers
   * is by setting an implementation of the `TokenVerifier` interface in the project's config.
   * The Authentication Booster Rocket for AWS uses this initialization mechanism.
   *
   * @deprecated [EOL v3] Please set your own implementation of the `TokenVerifier` interface in the project config.
   */
  private static loadTokenVerifierFromEnv(): void {
    const BOOSTER_JWT_ISSUER = process.env[JWT_ENV_VARS.BOOSTER_JWT_ISSUER]
    const BOOSTER_JWKS_URI = process.env[JWT_ENV_VARS.BOOSTER_JWKS_URI]
    const BOOSTER_ROLES_CLAIM = process.env[JWT_ENV_VARS.BOOSTER_ROLES_CLAIM]
    if (BOOSTER_JWT_ISSUER && BOOSTER_JWKS_URI && BOOSTER_ROLES_CLAIM) {
      console.warn(
        'Deprecation notice: Implicitly loading the JWT token verifier options from default environment variables is deprecated.' +
          " Please set your application's `config.tokenVerifiers` options explicitly in your `src/config/config.ts` file."
      )
      this.config.tokenVerifiers.push(
        new JwksUriTokenVerifier(BOOSTER_JWT_ISSUER, BOOSTER_JWKS_URI, BOOSTER_ROLES_CLAIM)
      )
    }
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
