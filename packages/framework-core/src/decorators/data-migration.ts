import { Booster } from '../booster'
import { DataMigrationInterface, DataMigrationParameters } from '@boostercloud/framework-types'

/**
 * Annotation to tell Booster which classes are data migration scripts
 * @param attributes
 * @constructor
 */
export function DataMigration(
  attributes: DataMigrationParameters
): (dataMigrationClass: DataMigrationInterface) => void {
  return (migrationClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.dataMigrationHandlers[migrationClass.name]) {
        throw new Error(`A data migration called ${migrationClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      config.dataMigrationHandlers[migrationClass.name] = {
        class: migrationClass,
        migrationOptions: attributes,
      }
    })
  }
}
