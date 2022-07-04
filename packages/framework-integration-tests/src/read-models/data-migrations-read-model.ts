import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult } from '@boostercloud/framework-types'
import { BoosterDataMigrationEntity } from '@boostercloud/framework-core/dist/core-concepts/data-migration/entities/booster-data-migration-entity'

@ReadModel({
  authorize: 'all',
})
export class DataMigrationsReadModel {
  public constructor(readonly id: string, readonly status: string, readonly lastUpdated: string) {}

  @Projects(BoosterDataMigrationEntity, 'id')
  public static updated(
    migration: BoosterDataMigrationEntity,
    oldMigration?: DataMigrationsReadModel
  ): ProjectionResult<DataMigrationsReadModel> {
    return new DataMigrationsReadModel(migration.id, migration.status.toString(), migration.lastUpdated)
  }
}
