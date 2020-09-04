import {
  BoosterConfig,
  CommandEnvelope,
  EventEnvelope,
  Logger,
  MigrationMetadata,
  CommandInterface,
  EntityInterface,
  EventInterface,
  InvalidVersionError,
} from '@boostercloud/framework-types'

type MigrableEnvelope = CommandEnvelope | EventEnvelope
type MigrableValue = CommandInterface | EventInterface | EntityInterface

export class Migrator {
  public constructor(private config: BoosterConfig, private logger: Logger) {}

  public migrate<TMigrableEnvelope extends MigrableEnvelope>(conceptEnvelope: TMigrableEnvelope): TMigrableEnvelope {
    this.checkVersionRange(conceptEnvelope)
    if (this.needsMigration(conceptEnvelope)) {
      return this.applyAllMigrations(conceptEnvelope)
    }

    return conceptEnvelope // The current version is exactly the same as the version of the concept
  }

  private checkVersionRange(conceptEnvelope: MigrableEnvelope): void {
    if (conceptEnvelope.version < 1) {
      throw new InvalidVersionError(
        `Received an invalid version value, ${conceptEnvelope.version}, for ${conceptEnvelope.typeName}. ` +
          'Versions must be greater than 0'
      )
    }

    const currentVersion = this.config.currentVersionFor(conceptEnvelope.typeName)
    if (currentVersion < conceptEnvelope.version) {
      throw new InvalidVersionError(
        `Can not migrate an unknown version: The current version of ${conceptEnvelope.typeName} is ${currentVersion}, which is ` +
          `lower than the received version ${conceptEnvelope.version}`
      )
    }
  }

  private needsMigration(conceptEnvelope: MigrableEnvelope): boolean {
    const currentVersion = this.config.currentVersionFor(conceptEnvelope.typeName)
    return currentVersion > conceptEnvelope.version
  }

  private applyAllMigrations<TMigrableEnvelope extends MigrableEnvelope>(
    oldConceptEnvelope: TMigrableEnvelope
  ): TMigrableEnvelope {
    const currentVersion = this.config.currentVersionFor(oldConceptEnvelope.typeName)
    const oldVersion = oldConceptEnvelope.version
    this.logger.info(`Migrating ${oldConceptEnvelope.typeName} from version ${oldVersion} to version ${currentVersion}`)
    this.logger.debug('Envelope before migration:\n', oldConceptEnvelope)

    const migrations = this.config.migrations[oldConceptEnvelope.typeName]
    let migratedConceptValue = oldConceptEnvelope.value as MigrableValue
    for (let toVersion = oldVersion + 1; toVersion <= currentVersion; toVersion++) {
      migratedConceptValue = this.applyMigration(migratedConceptValue, migrations.get(toVersion))
    }

    const newConceptEnvelope = {
      ...oldConceptEnvelope,
      value: migratedConceptValue,
      version: currentVersion,
    }
    this.logger.debug('Envelope after migration:\n', newConceptEnvelope)
    return newConceptEnvelope
  }

  private applyMigration<TMigrableValue extends MigrableValue>(
    oldValue: TMigrableValue,
    migration: MigrationMetadata | undefined
  ): TMigrableValue {
    if (!migration) {
      throw new InvalidVersionError(
        'Received an undefined migration value. Are there "gaps" between the versions of the migrations?'
      )
    }
    const oldConcept = Object.assign(new migration.fromSchema(), oldValue)
    const migrationMethod = new migration.migrationClass()[migration.methodName]
    const newConcept = migrationMethod(oldConcept)
    this.logger.debug(`Partial migration finished. Migrated from oldValue=${oldConcept} to newValue=${newConcept}`)
    return newConcept
  }
}
