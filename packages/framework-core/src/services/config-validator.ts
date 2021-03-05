import { BoosterConfig, MigrationMetadata } from '@boostercloud/framework-types'

export class ConfigValidator {
  /** Validates a BoosterConfig object */
  public static validate(config: BoosterConfig): void {
    validateAllMigrations(config)
  }
}

function validateAllMigrations(config: BoosterConfig): void {
  for (const conceptName in config.migrations) {
    validateConceptMigrations(config, conceptName, config.migrations[conceptName])
  }
}

function validateConceptMigrations(
  config: BoosterConfig,
  conceptName: string,
  migrations: Map<number, MigrationMetadata>
): void {
  // Check that migrations are defined consecutively. In other words, there are no gaps between the version numbers
  const currentVersion = config.currentVersionFor(conceptName)
  for (let toVersion = 2; toVersion <= currentVersion; toVersion++) {
    if (!migrations.has(toVersion)) {
      throw new Error(
        `Migrations for '${conceptName}' are invalid: they are missing a migration with toVersion=${toVersion}. ` +
          `There must be a migration for '${conceptName}' for every version in the range [2..${currentVersion}]`
      )
    }
  }
}
