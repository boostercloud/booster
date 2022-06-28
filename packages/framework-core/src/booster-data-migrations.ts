import {
  DataMigrationInterface,
  DataMigrationMetadata,
  DataMigrationStatus,
  EntityInterface,
  EventEnvelope,
  Instance,
  PaginatedEntitiesIdsResult,
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

export class BoosterDataMigrations {
  public static async run(): Promise<boolean> {
    const config = Booster.config
    const logger = getLogger(config, 'BoosterDataMigrationDispatcher#dispatch')
    let migrating = false

    const configuredMigrations = config.dataMigrationHandlers
    if (Object.keys(configuredMigrations).length === 0) {
      logger.debug('No pending migrations. Skipping...')
      return false
    }

    const sortedConfiguredMigrations = BoosterDataMigrations.sortConfiguredMigrations(configuredMigrations)
    const migrationEntities = await BoosterDataMigrations.latestDataMigrationEntities()

    for (const configuredMigration of Object.values(sortedConfiguredMigrations)) {
      const migrationEntityForConfiguredMigration = BoosterDataMigrations.migrationEntitiesForConfiguredMigration(
        migrationEntities,
        configuredMigration.class.name
      )

      if (!BoosterDataMigrations.hasItems(migrationEntityForConfiguredMigration)) {
        logger.debug('Not found running or finished migrations for the DataMigration', configuredMigration)
        migrating = true
        await BoosterDataMigrations.migrate(configuredMigration)
      } else {
        const runningMigrationsForHandler = BoosterDataMigrations.runningMigrations(
          migrationEntityForConfiguredMigration
        )
        if (BoosterDataMigrations.hasItems(runningMigrationsForHandler)) {
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
    const register = new Register(requestID, {})
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

  private static async latestDataMigrationEntities(): Promise<Array<EventEnvelope>> {
    const ids = await BoosterDataMigrations.dataMigrationEntitiesIds()
    return await BoosterDataMigrations.fetchEntities(ids)
  }

  private static async fetchEntities(ids: Array<UUID>): Promise<Array<EventEnvelope>> {
    const eventStore = new EventStore(Booster.config)
    const promises: Array<Promise<EventEnvelope | null>> = []
    for (const id of ids) {
      promises.push(eventStore.fetchEntitySnapshot(BoosterDataMigrationEntity.name, id))
    }
    const result = await Promise.all(promises)
    return result.filter((item) => item !== null) as Array<EventEnvelope>
  }

  private static async dataMigrationEntitiesIds(): Promise<Array<UUID>> {
    let count = 1
    const limit = 10
    let cursor: Record<'id', string> | undefined = undefined
    const result: Array<UUID> = []
    while (count > 0) {
      const queryResult: PaginatedEntitiesIdsResult = await Booster.config.provider.events.searchEntitiesIDs(
        Booster.config,
        limit,
        cursor,
        BoosterDataMigrationEntity.name
      )

      cursor = queryResult.cursor
      count = queryResult.count ?? 0
      result.push(...queryResult.items.map((item) => item.entityID))
    }

    return result
  }

  private static migrationEntitiesForConfiguredMigration(
    runningOrFinishedMigrations: Array<EventEnvelope>,
    configuredMigrationName: string
  ): Array<EventEnvelope> {
    return runningOrFinishedMigrations.filter((entity) => {
      const entityValue = entity.value as BoosterDataMigrationEntity
      return entityValue && entityValue.id === configuredMigrationName
    })
  }

  private static hasItems(eventEnvelopes: EventEnvelope[]): boolean {
    return eventEnvelopes && eventEnvelopes.length > 0
  }

  private static async migrate(migrationHandler: DataMigrationMetadata): Promise<void> {
    const startedRegister = new Register(UUID.generate(), {})

    await BoosterDataMigrations.emitStarted(startedRegister, migrationHandler.class.name)
    await RegisterHandler.handle(Booster.config, startedRegister)

    const finishedRegister = new Register(UUID.generate(), {})
    await (migrationHandler.class as DataMigrationInterface).start(finishedRegister)
    await RegisterHandler.handle(Booster.config, finishedRegister)
  }

  private static async emitStarted(register: Register, configuredMigrationName: string): Promise<void> {
    const logger = getLogger(Booster.config, 'BoosterMigration#emitStarted')
    logger.info('Migration started', configuredMigrationName)
    register.events(new BoosterDataMigrationStarted(configuredMigrationName, new Date().toISOString()))
  }

  private static runningMigrations(
    runningOrFinishedMigrationForConfiguredMigration: Array<EventEnvelope>
  ): Array<EventEnvelope> {
    return runningOrFinishedMigrationForConfiguredMigration.filter((entity) => {
      const value = entity.value as BoosterDataMigrationEntity
      return value && value.status === DataMigrationStatus.RUNNING
    })
  }
}
