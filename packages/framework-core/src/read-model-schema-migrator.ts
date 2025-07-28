import {
  BoosterConfig,
  FilterFor,
  InvalidVersionError,
  ReadModelInterface,
  ReadModelListResult,
  SchemaMigrationMetadata,
  TraceActionTypes,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { Trace } from './instrumentation'

export class ReadModelSchemaMigrator {
  private static readonly LIMIT = 100

  public constructor(private config: BoosterConfig) {}

  /**
   * **NOTE:** Read model schema migration is deprecated. Prefer data migration.
   */
  @Trace(TraceActionTypes.READ_MODEL_SCHEMA_MIGRATOR_MIGRATE)
  public async migrate<TMigratableReadModel extends ReadModelInterface>(
    readModel: TMigratableReadModel,
    readModelName: string
  ): Promise<TMigratableReadModel> {
    this.checkVersionRange(readModel, readModelName)
    if (this.needsMigration(readModel, readModelName)) {
      return await this.applyAllMigrations(readModel, readModelName)
    }

    return readModel // The current version is exactly the same as the version of the concept
  }

  public async migrateAll(readModelName: string, batchSize = ReadModelSchemaMigrator.LIMIT): Promise<number> {
    const filterFor = this.buildFilterForSearchReadModelsToMigrate(readModelName)
    let cursor: Record<'id', string> | undefined = undefined
    let total = 0
    do {
      const toMigrate: ReadModelListResult<ReadModelInterface> = await this.searchReadModelsToMigrate(
        readModelName,
        filterFor,
        batchSize,
        cursor
      )
      cursor = toMigrate.items.length >= batchSize ? toMigrate.cursor : undefined
      const migrationPromises = toMigrate.items.map((item) => this.applyAllMigrations(item, readModelName))
      const migratedReadModels = await Promise.all(migrationPromises)
      const persistPromises = migratedReadModels.map((readModel) => this.persistReadModel(readModel, readModelName))
      await Promise.all(persistPromises)
      total += toMigrate.items.length
    } while (cursor)
    return total
  }

  private checkVersionRange(readModel: ReadModelInterface, readModelName: string): void {
    const readModelVersion = ReadModelSchemaMigrator.readModelSchemaVersion(readModel)
    if (readModelVersion < 1) {
      throw new InvalidVersionError(
        `Received an invalid schema version value, ${readModelVersion}, for ${readModelName}. ` +
          'Versions must be greater than 0'
      )
    }

    const currentVersion = this.config.currentVersionFor(readModelName)
    if (currentVersion < readModelVersion) {
      throw new InvalidVersionError(
        `Can not migrate schema an unknown version: The current schema version of ${readModelName} is ${currentVersion}, which is ` +
          `lower than the received version ${readModelVersion}`
      )
    }
  }

  private needsMigration(readModel: ReadModelInterface, readModelName: string): boolean {
    const currentVersion = this.config.currentVersionFor(readModelName)
    return currentVersion > ReadModelSchemaMigrator.readModelSchemaVersion(readModel)
  }

  private async applyAllMigrations<TMigratableReadModel extends ReadModelInterface>(
    oldReadModel: TMigratableReadModel,
    readModelName: string
  ): Promise<TMigratableReadModel> {
    const logger = getLogger(this.config, 'ReadModelSchemaMigrator#applyAllMigrations')
    const oldVersion = ReadModelSchemaMigrator.readModelSchemaVersion(oldReadModel)
    const currentVersion = this.config.currentVersionFor(readModelName)
    logger.info(`Migrating Schema ${readModelName} from version ${oldVersion} to version ${currentVersion}`)
    logger.debug('ReadModel before schema migration:\n', oldReadModel)

    const migrations = this.config.schemaMigrations[readModelName]
    let migratedValue = oldReadModel
    for (let toVersion = oldVersion + 1; toVersion <= currentVersion; toVersion++) {
      migratedValue = await this.applyMigration(migratedValue, migrations.get(toVersion))
    }

    const newReadModel = Object.assign(migratedValue, {
      boosterMetadata: {
        ...oldReadModel.boosterMetadata,
        schemaVersion: currentVersion,
      },
    })

    logger.debug('ReadModel after schema migration:\n', newReadModel)
    return newReadModel
  }

  private async applyMigration<TMigrableValue extends ReadModelInterface>(
    oldValue: TMigrableValue,
    migration: SchemaMigrationMetadata | undefined
  ): Promise<TMigrableValue> {
    const logger = getLogger(this.config, 'ReadModelSchemaMigrator#applyMigration')
    if (!migration) {
      throw new InvalidVersionError(
        'Received an undefined schema migration value. Are there "gaps" between the versions of the schema migrations?'
      )
    }
    const oldConcept = Object.assign(new migration.fromSchema(), oldValue)
    const migrationMethod = new migration.migrationClass()[migration.methodName]
    const newConcept = await migrationMethod(oldConcept)
    logger.debug(
      `Partial schema migration finished. Schema migrated from oldValue=${JSON.stringify(
        oldConcept
      )} to newValue=${JSON.stringify(newConcept)}`
    )
    return newConcept
  }

  private static readModelSchemaVersion(readModel: ReadModelInterface): number {
    return readModel.boosterMetadata?.schemaVersion ?? 1
  }

  private buildFilterForSearchReadModelsToMigrate(readModelName: string): FilterFor<ReadModelInterface> {
    const expectedVersion = this.config.currentVersionFor(readModelName)
    return {
      or: [
        {
          boosterMetadata: {
            schemaVersion: {
              lt: expectedVersion,
            },
          },
        },
        {
          boosterMetadata: {
            schemaVersion: {
              isDefined: false,
            },
          },
        },
      ],
    }
  }

  private async searchReadModelsToMigrate(
    readModelName: string,
    filterFor: FilterFor<ReadModelInterface>,
    limit: number,
    cursor: undefined | Record<'id', string>
  ): Promise<ReadModelListResult<ReadModelInterface>> {
    return (await this.config.provider.readModels.search<ReadModelInterface>(
      this.config,
      readModelName,
      filterFor,
      {},
      limit,
      cursor,
      true
    )) as ReadModelListResult<ReadModelInterface>
  }

  private persistReadModel(newReadModel: ReadModelInterface, readModelName: string): Promise<unknown> {
    const logger = getLogger(this.config, 'ReadModelSchemaMigrator#persistReadModel')
    if (!(newReadModel && newReadModel.boosterMetadata)) {
      throw new Error(`Error migrating ReadModel: ${newReadModel}`)
    }
    const currentReadModelVersion: number = newReadModel?.boosterMetadata?.version ?? 0
    const schemaVersion: number =
      newReadModel?.boosterMetadata?.schemaVersion ?? this.config.currentVersionFor(readModelName)
    newReadModel.boosterMetadata = {
      ...newReadModel?.boosterMetadata,
      version: currentReadModelVersion + 1,
      schemaVersion: schemaVersion,
    }
    logger.debug('Storing new version of read model', newReadModel)
    return this.config.provider.readModels.store(this.config, readModelName, newReadModel, currentReadModelVersion)
  }
}
