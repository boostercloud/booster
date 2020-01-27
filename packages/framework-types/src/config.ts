import {
  ReducerMetadata,
  MigrationMetadata,
  EntityMetadata,
  RoleMetadata,
  CommandMetadata,
  ProjectionMetadata,
} from './concepts'
import { Provider } from './provider'
import { Level } from './logger'
import { ReadModelMetadata } from './concepts/read-model'

/**
 * Class used by external packages that needs to get a representation of
 * the booster config. Used mainly for vendor-specific deployment packages
 */
export class BoosterConfig {
  public logLevel: Level = Level.debug
  public provider: Provider = Provider.AWS
  public appName = 'new-booster-app'
  public region = 'eu-west-1'
  public readonly userProjectRootPath: string = process.cwd()
  public readonly commandDispatcherHandler: string = 'dist/index.boosterCommandDispatcher'
  public readonly eventDispatcherHandler: string = 'dist/index.boosterEventDispatcher'
  public readonly readModelMapperHandler: string = 'dist/index.boosterReadModelMapper'
  public readonly preSignUpHandler: string = 'dist/index.boosterPreSignUpChecker'

  public readonly entities: Record<EntityName, EntityMetadata> = {}
  public readonly reducers: Record<EventName, ReducerMetadata> = {}
  public readonly commandHandlers: Record<CommandName, CommandMetadata> = {}
  public readonly readModels: Record<ReadModelName, ReadModelMetadata> = {}
  public readonly projections: Record<EntityName, Array<ProjectionMetadata>> = {}
  public readonly roles: Record<RoleName, RoleMetadata> = {}
  public readonly migrations: Record<ConceptName, Map<Version, MigrationMetadata>> = {}

  public get resourceNames(): ResourceNames {
    if (this.appName.length === 0) throw new Error('Application name cannot be empty')
    const applicationStackName = this.appName + '-application-stack'
    return {
      applicationStack: applicationStackName,
      eventsStream: applicationStackName + '-events-stream',
      eventsStore: applicationStackName + '-events-store',
      forReadModel(readModelName: string): string {
        return applicationStackName + '-' + readModelName
      },
    }
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
  eventsStream: string
  eventsStore: string
  forReadModel(entityName: string): string
}

type EntityName = string
type EventName = string
type CommandName = string
type ReadModelName = string
type RoleName = string
type ConceptName = string
type Version = number
