import { Booster } from '../booster'
import { Class, AnyClass, SchemaMigrationMetadata, BoosterConfig, Instance } from '@boostercloud/framework-types'
import 'reflect-metadata'

const migrationMethodsMetadataKey = 'booster:migrationsMethods'

export function SchemaMigration(conceptClass: AnyClass): (schemaMigrationClass: AnyClass) => void {
  return (schemaMigrationClass) => {
    Booster.configureCurrentEnv((config) => {
      const conceptMigrations = getConceptMigrations(config, conceptClass)
      const migrationMethodsMetadata = getMigrationMethods(schemaMigrationClass)

      for (const schemaMigrationMetadata of migrationMethodsMetadata) {
        if (conceptMigrations.has(schemaMigrationMetadata.toVersion)) {
          throw new Error(
            `Found duplicated migration for '${conceptClass.name}' in migration class '${schemaMigrationClass.name}': ` +
              `There is an already defined migration for version ${schemaMigrationMetadata.toVersion}`
          )
        }

        conceptMigrations.set(schemaMigrationMetadata.toVersion, schemaMigrationMetadata)
      }
    })
  }
}

function getConceptMigrations(config: BoosterConfig, conceptClass: AnyClass): Map<number, SchemaMigrationMetadata> {
  if (!config.schemaMigrations[conceptClass.name]) {
    config.schemaMigrations[conceptClass.name] = new Map()
  }
  return config.schemaMigrations[conceptClass.name]
}

function getMigrationMethods(migrationClass: AnyClass): Array<SchemaMigrationMetadata> {
  const migrationMethods: Array<SchemaMigrationMetadata> = Reflect.getMetadata(
    migrationMethodsMetadataKey,
    migrationClass
  )
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

    let migrationMethods: Array<SchemaMigrationMetadata> = Reflect.getMetadata(
      migrationMethodsMetadataKey,
      migrationClass
    )
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

type MigrationMethod<TOldSchema, TNewSchema> = TypedPropertyDescriptor<(old: TOldSchema) => Promise<TNewSchema>>
