import { Booster } from '../booster'
import { Class, AnyClass, MigrationMetadata, BoosterConfig, Instance } from '@boostercloud/framework-types'
import 'reflect-metadata'

const migrationMethodsMetadataKey = 'booster:migrationsMethods'

export function Migrates(conceptClass: AnyClass): (migrationClass: AnyClass) => void {
  return (migrationClass) => {
    Booster.configureCurrentEnv((config) => {
      const conceptMigrations = getConceptMigrations(config, conceptClass)
      const migrationMethodsMetadata = getMigrationMethods(migrationClass)

      for (const migrationMetadata of migrationMethodsMetadata) {
        if (conceptMigrations.has(migrationMetadata.toVersion)) {
          throw new Error(
            `Found duplicated migration for '${conceptClass.name}' in migration class '${migrationClass.name}': ` +
              `There is an already defined migration for version ${migrationMetadata.toVersion}`
          )
        }

        conceptMigrations.set(migrationMetadata.toVersion, migrationMetadata)
      }
    })
  }
}

function getConceptMigrations(config: BoosterConfig, conceptClass: AnyClass): Map<number, MigrationMetadata> {
  if (!config.migrations[conceptClass.name]) {
    config.migrations[conceptClass.name] = new Map()
  }
  return config.migrations[conceptClass.name]
}

function getMigrationMethods(migrationClass: AnyClass): Array<MigrationMetadata> {
  const migrationMethods: Array<MigrationMetadata> = Reflect.getMetadata(migrationMethodsMetadataKey, migrationClass)
  if (!migrationMethods || migrationMethods.length == 0) {
    throw new Error(
      'No migration methods found in this class. Define at least one migration and annotate it with @ToVersion()'
    )
  }
  return migrationMethods
}

/**
 * Decorator to tell Booster the version you are migrating to
 * @param toVersion
 * @param props
 */
export function ToVersion<TOldSchema, TNewSchema>(
  toVersion: number,
  props: {
    fromSchema: Class<TOldSchema>
    toSchema: Class<TNewSchema>
  }
): (
  migrationInstance: Instance,
  propertyName: string,
  propertyDescriptor: MigrationMethod<TOldSchema, TNewSchema>
) => void {
  if (toVersion <= 1) {
    throw new Error('Migration versions must always be greater than 1')
  }

  return (migrationInstance, propertyName): void => {
    const migrationClass = migrationInstance.constructor as AnyClass

    let migrationMethods: Array<MigrationMetadata> = Reflect.getMetadata(migrationMethodsMetadataKey, migrationClass)
    if (!migrationMethods) {
      migrationMethods = []
    }

    migrationMethods.push({
      migrationClass,
      methodName: propertyName,
      toVersion,
      fromSchema: props.fromSchema,
      toSchema: props.toSchema,
    })

    // Here we just store the information (version and method). All the checks will be done in the @Migrates decorator
    Reflect.defineMetadata(migrationMethodsMetadataKey, migrationMethods, migrationClass)
  }
}

type MigrationMethod<TOldSchema, TNewSchema> = TypedPropertyDescriptor<(old: TOldSchema) => TNewSchema>
