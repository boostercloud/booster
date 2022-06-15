import {
  BoosterConfig,
  InvalidVersionError,
  MigrationMetadata,
  ReadModelInterface,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export class ReadModelMigrator {
  public constructor(private config: BoosterConfig) {}

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

  private checkVersionRange(readModel: ReadModelInterface, readModelName: string): void {
    const readModelVersion = ReadModelMigrator.readModelSchemaVersion(readModel)
    if (readModelVersion < 1) {
      throw new InvalidVersionError(
        `Received an invalid version value, ${readModelVersion}, for ${readModelName}. ` +
          'Versions must be greater than 0'
      )
    }

    const currentVersion = this.config.currentVersionFor(readModelName)
    if (currentVersion < readModelVersion) {
      throw new InvalidVersionError(
        `Can not migrate an unknown version: The current version of ${readModelName} is ${currentVersion}, which is ` +
          `lower than the received version ${readModelVersion}`
      )
    }
  }

  private needsMigration(readModel: ReadModelInterface, readModelName: string): boolean {
    const currentVersion = this.config.currentVersionFor(readModelName)
    return currentVersion > ReadModelMigrator.readModelSchemaVersion(readModel)
  }

  private async applyAllMigrations<TMigratableReadModel extends ReadModelInterface>(
    oldReadModel: TMigratableReadModel,
    readModelName: string
  ): Promise<TMigratableReadModel> {
    const logger = getLogger(this.config, 'ReadModelMigrator#applyAllMigrations')
    const oldVersion = ReadModelMigrator.readModelSchemaVersion(oldReadModel)
    const currentVersion = this.config.currentVersionFor(readModelName)
    logger.info(`Migrating ${readModelName} from version ${oldVersion} to version ${currentVersion}`)
    logger.debug('ReadModel before migration:\n', oldReadModel)

    const migrations = this.config.migrations[readModelName]
    let migratedValue = oldReadModel
    for (let toVersion = oldVersion + 1; toVersion <= currentVersion; toVersion++) {
      migratedValue = await this.applyMigration(migratedValue, migrations.get(toVersion))
    }

    const newReadModel = {
      ...migratedValue,
      boosterMetadata: {
        ...oldReadModel.boosterMetadata,
        schemaVersion: currentVersion,
      },
    }
    logger.debug('ReadModel after migration:\n', newReadModel)
    return newReadModel
  }

  private async applyMigration<TMigrableValue extends ReadModelInterface>(
    oldValue: TMigrableValue,
    migration: MigrationMetadata | undefined
  ): Promise<TMigrableValue> {
    const logger = getLogger(this.config, 'ReadModelMigrator#applyMigration')
    if (!migration) {
      throw new InvalidVersionError(
        'Received an undefined migration value. Are there "gaps" between the versions of the migrations?'
      )
    }
    const oldConcept = Object.assign(new migration.fromSchema(), oldValue)
    const migrationMethod = new migration.migrationClass()[migration.methodName]
    const newConcept = await migrationMethod(oldConcept)
    logger.debug(
      `Partial migration finished. Migrated from oldValue=${JSON.stringify(oldConcept)} to newValue=${JSON.stringify(
        newConcept
      )}`
    )
    return newConcept
  }

  private static readModelSchemaVersion(readModel: ReadModelInterface): number {
    return readModel.boosterMetadata?.schemaVersion ?? 1
  }
}
