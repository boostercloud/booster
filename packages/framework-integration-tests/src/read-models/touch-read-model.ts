import { BoosterTouchEntityEntity, Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult } from '@boostercloud/framework-types'
import { DataMigrationsReadModel } from './data-migrations-read-model'

@ReadModel({
  authorize: 'all',
})
export class TouchReadModel {
  public constructor(readonly id: string, readonly status: string, readonly lastUpdated: string) {}

  @Projects(BoosterTouchEntityEntity, 'id')
  public static updated(
    touchEntity: BoosterTouchEntityEntity,
    _oldTouchReadModel?: DataMigrationsReadModel
  ): ProjectionResult<TouchReadModel> {
    return new TouchReadModel(touchEntity.id, touchEntity.status.toString(), touchEntity.lastUpdated)
  }
}
