import {
  BoosterConfig,
  InvalidVersionError,
  MigrationMetadata,
  ReadModelInterface,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export class ReadModelMigrator {
  public constructor(private config: BoosterConfig, readonly readModelName: string) {}

  public async migrate<TMigratableReadModel extends ReadModelInterface>(
    readModel: TMigratableReadModel
  ): Promise<TMigratableReadModel> {
    this.checkVersionRange(readModel)
    if (this.needsMigration(readModel)) {
      return await this.applyAllMigrations(readModel)
    }

    return readModel // The current version is exactly the same as the version of the concept
  }

  private checkVersionRange(readModel: ReadModelInterface): void {
    const readModelVersion = ReadModelMigrator.readModelSchemaVersion(readModel)
    const currentVersion = this.config.currentVersionFor(this.readModelName)
    if (currentVersion < readModelVersion) {
      throw new InvalidVersionError(
        `Can not migrate an unknown version: The current version of ${this.readModelName} is ${currentVersion}, which is ` +
          `lower than the received version ${readModelVersion}`
      )
    }
  }

  private needsMigration(readModel: ReadModelInterface): boolean {
    const currentVersion = this.config.currentVersionFor(this.readModelName)
    return currentVersion > ReadModelMigrator.readModelSchemaVersion(readModel)
  }

  private async applyAllMigrations<TMigratableReadModel extends ReadModelInterface>(
    oldReadModel: TMigratableReadModel
  ): Promise<TMigratableReadModel> {
    const logger = getLogger(this.config, 'Migrator#applyAllMigrations')
    const oldVersion = ReadModelMigrator.readModelSchemaVersion(oldReadModel)
    const currentVersion = this.config.currentVersionFor(this.readModelName)
    logger.info(`Migrating ${this.readModelName} from version ${oldVersion} to version ${currentVersion}`)
    logger.debug('ReadModel before migration:\n', oldReadModel)

    const migrations = this.config.migrations[this.readModelName]
    let migratedValue = oldReadModel
    for (let toVersion = oldVersion + 1; toVersion <= currentVersion; toVersion++) {
      migratedValue = await this.applyMigration(migratedValue, migrations.get(toVersion))
    }

    const newReadModel = {
      ...migratedValue,
      boosterMetadata: {
        ...migratedValue.boosterMetadata,
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
    const logger = getLogger(this.config, 'Migrator#applyMigration')
    if (!migration) {
      throw new InvalidVersionError(
        'Received an undefined migration value. Are there "gaps" between the versions of the migrations?'
      )
    }
    const oldConcept = Object.assign(new migration.fromSchema(), oldValue)
    const migrationMethod = new migration.migrationClass()[migration.methodName]
    const newConcept = await migrationMethod(oldConcept) // todo user should update the boosterMetadata.version if needed or we could do it here
    logger.debug(`Partial migration finished. Migrated from oldValue=${oldConcept} to newValue=${newConcept}`)
    return newConcept
  }

  private static readModelSchemaVersion(readModel: ReadModelInterface): number {
    return readModel.boosterMetadata?.schemaVersion || 1
  }
}
