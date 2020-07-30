import {
  ReducerMetadata,
  MigrationMetadata,
  EntityMetadata,
  RoleMetadata,
  CommandMetadata,
  ProjectionMetadata,
  ReadModelMetadata,
  EventHandlerInterface,
} from './concepts'
import { ProviderLibrary } from './provider'
import { Level } from './logger'

/**
 * Class used by external packages that needs to get a representation of
 * the booster config. Used mainly for vendor-specific deployment packages
 */
export class BoosterConfig {
  public logLevel: Level = Level.debug
  private readonly _configuredEnvironments: Set<string> = new Set<string>()
  private _provider?: ProviderLibrary
  public appName = 'new-booster-app'
  public region = 'eu-west-1'
  public readonly userProjectRootPath: string = process.cwd()
  public readonly eventDispatcherHandler: string = 'dist/index.boosterEventDispatcher'
  public readonly preSignUpHandler: string = 'dist/index.boosterPreSignUpChecker'
  public readonly serveGraphQLHandler: string = 'dist/index.boosterServeGraphQL'
  public readonly notifySubscribersHandler: string = 'dist/index.boosterNotifySubscribers'

  public readonly entities: Record<EntityName, EntityMetadata> = {}
  public readonly reducers: Record<EventName, ReducerMetadata> = {}
  public readonly commandHandlers: Record<CommandName, CommandMetadata> = {}
  public readonly eventHandlers: Record<EventName, Array<EventHandlerInterface>> = {}
  public readonly readModels: Record<ReadModelName, ReadModelMetadata> = {}
  public readonly projections: Record<EntityName, Array<ProjectionMetadata>> = {}
  public readonly roles: Record<RoleName, RoleMetadata> = {}
  public readonly migrations: Record<ConceptName, Map<Version, MigrationMetadata>> = {}

  /** Environment variables set at deployment time on the target lambda functions */
  public readonly env: Record<string, string> = {}

  public constructor(public readonly environmentName: string) {}

  public get resourceNames(): ResourceNames {
    if (this.appName.length === 0) throw new Error('Application name cannot be empty')
    const applicationStackName = this.appName + '-app'
    return {
      applicationStack: applicationStackName,
      eventsStore: applicationStackName + '-events-store',
      subscriptionsStore: applicationStackName + '-subscriptions-store',
      connectionsStore: applicationStackName + '-connections-store',
      staticWebsite: applicationStackName + '-static-site',
      forReadModel(readModelName: string): string {
        return applicationStackName + '-' + readModelName
      },
    }
  }

  /**
   * Returns the name of the ReadModel from the name of its resouce (normally, a table)
   * @param resourceName
   */
  public readModelNameFromResourceName(resourceName: string): string {
    const resourceNamePrefixRegex = new RegExp(`^${this.resourceNames.applicationStack}-`)
    return resourceName.replace(resourceNamePrefixRegex, '')
  }

  /**
   * This is a convenient property to easily check if the application has defined some roles.
   * Only in that case we will create a user pool and an authorization API.
   * If there are no roles defined, it means that the app is completely public and users
   * won't be registered (they are all anonymous)
   */
  public get thereAreRoles(): boolean {
    return Object.entries(this.roles).length > 0
  }

  public currentVersionFor(className: string): number {
    const migrations = this.migrations[className]
    if (!migrations) {
      return 1
    }

    return Math.max(...migrations.keys())
  }

  public validate(): void {
    this.validateAllMigrations()
  }

  public get provider(): ProviderLibrary {
    if (!this._provider) throw new Error('It is required to set a valid provider runtime in your configuration files')
    return this._provider
  }

  public set provider(provider: ProviderLibrary) {
    this._provider = provider
  }

  public mustGetEnvironmentVar(varName: string): string {
    const value = process.env[varName]
    if (value == undefined) {
      throw new Error(`Missing environment variable '${varName}'`)
    }
    return value
  }

  public addConfiguredEnvironment(environmentName: string): void {
    this._configuredEnvironments.add(environmentName)
  }

  public get configuredEnvironments(): Set<string> {
    return this._configuredEnvironments
  }

  private validateAllMigrations(): void {
    for (const conceptName in this.migrations) {
      this.validateConceptMigrations(conceptName, this.migrations[conceptName])
    }
  }

  private validateConceptMigrations(conceptName: string, migrations: Map<number, MigrationMetadata>): void {
    // Check that migrations are defined consecutively. In other words, there are no gaps between the version numbers
    const currentVersion = this.currentVersionFor(conceptName)
    for (let toVersion = 2; toVersion <= currentVersion; toVersion++) {
      if (!migrations.has(toVersion)) {
        throw new Error(
          `Migrations for '${conceptName}' are invalid: they are missing a migration with toVersion=${toVersion}. ` +
            `There must be a migration for '${conceptName}' for every version in the range [2..${currentVersion}]`
        )
      }
    }
  }
}

interface ResourceNames {
  applicationStack: string
  eventsStore: string
  subscriptionsStore: string
  connectionsStore: string
  staticWebsite: string
  forReadModel(entityName: string): string
}

type EntityName = string
type EventName = string
type CommandName = string
type ReadModelName = string
type RoleName = string
type ConceptName = string
type Version = number
