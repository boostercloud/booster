import {
  TraceActionTypes,
  DataMigrationInterface,
  DataMigrationMetadata,
  DataMigrationStatus,
  EntityInterface,
  Instance,
  Register,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { RegisterHandler } from './booster-register-handler'
import { EventStore } from './services/event-store'
import { BoosterDataMigrationEntity } from './core-concepts/data-migration/entities/booster-data-migration-entity'
import { BoosterEntityMigrated } from './core-concepts/data-migration/events/booster-entity-migrated'
import { Booster } from './index'
import { BoosterDataMigrationStarted } from './core-concepts/data-migration/events/booster-data-migration-started'
import { Trace } from './instrumentation'

export class BoosterDataMigrations {
  @Trace(TraceActionTypes.MIGRATION_RUN)
  public static async run(): Promise<boolean> {
    const config = Booster.config
    const logger = getLogger(config, 'BoosterDataMigrationDispatcher#dispatch')
    let migrating = false

    const configuredMigrations = config.dataMigrationHandlers
    if (Object.keys(configuredMigrations).length === 0) {
      logger.debug('No defined migrations found. Skipping...')
      return false
    }

    const sortedConfiguredMigrations = BoosterDataMigrations.sortConfiguredMigrations(configuredMigrations)
    const eventStore = new EventStore(config)
    for (const configuredMigration of Object.values(sortedConfiguredMigrations)) {
      const migrationEntityForConfiguredMigration = await eventStore.fetchEntitySnapshot(
        BoosterDataMigrationEntity.name,
        configuredMigration.class.name
      )
      if (!migrationEntityForConfiguredMigration) {
        logger.debug('Not found running or finished migrations for the DataMigration', configuredMigration)
        migrating = true
        await BoosterDataMigrations.migrate(configuredMigration)
      } else {
        const boosterDataMigrationEntity = migrationEntityForConfiguredMigration.value as BoosterDataMigrationEntity
        if (boosterDataMigrationEntity.status === DataMigrationStatus.RUNNING) {
          logger.debug('Found running migrations for the DataMigration', configuredMigration)
          migrating = true
        }
      }
    }

    return migrating
  }

  public static migrateEntity(
    oldEntityName: string,
    oldEntityId: UUID,
    newEntity: Instance & EntityInterface
  ): Promise<void> {
    const requestID = UUID.generate()
    const register = new Register(requestID, {}, RegisterHandler.flush)
    register.events(new BoosterEntityMigrated(oldEntityName, oldEntityId, newEntity.constructor.name, newEntity))
    return RegisterHandler.handle(Booster.config, register)
  }

  private static sortConfiguredMigrations(
    configuredMigrations: Record<string, DataMigrationMetadata>
  ): Array<DataMigrationMetadata> {
    return Object.values(configuredMigrations).sort((a: DataMigrationMetadata, b: DataMigrationMetadata) => {
      return a.migrationOptions.order - b.migrationOptions.order
    })
  }

  private static async migrate(migrationHandler: DataMigrationMetadata): Promise<void> {
    const startedRegister = new Register(UUID.generate(), {}, RegisterHandler.flush)

    await BoosterDataMigrations.emitStarted(startedRegister, migrationHandler.class.name)
    await RegisterHandler.handle(Booster.config, startedRegister)

    const finishedRegister = new Register(UUID.generate(), {}, RegisterHandler.flush)
    await (migrationHandler.class as DataMigrationInterface).start(finishedRegister)
    await RegisterHandler.handle(Booster.config, finishedRegister)
  }

  private static async emitStarted(register: Register, configuredMigrationName: string): Promise<void> {
    const logger = getLogger(Booster.config, 'BoosterMigration#emitStarted')
    logger.info('Migration started', configuredMigrationName)
    register.events(new BoosterDataMigrationStarted(configuredMigrationName, new Date().toISOString()))
  }
}
