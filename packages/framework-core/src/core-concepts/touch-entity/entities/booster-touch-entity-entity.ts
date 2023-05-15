import { BoosterEntityTouchStarted } from '../events/booster-entity-touch-started'
import { BoosterEntityTouchFinished } from '../events/booster-entity-touch-finished'
import { EntityTouchStatus } from '@boostercloud/framework-types'

export class BoosterTouchEntityEntity {
  public constructor(public id: string, public status: EntityTouchStatus, public lastUpdated: string) {}

  public static started(
    event: BoosterEntityTouchStarted,
    currentEntityTouch: BoosterTouchEntityEntity
  ): BoosterTouchEntityEntity {
    return new BoosterTouchEntityEntity(event.name, EntityTouchStatus.RUNNING, event.lastUpdated)
  }

  public static finished(
    event: BoosterEntityTouchFinished,
    currentEntityTouch: BoosterTouchEntityEntity
  ): BoosterTouchEntityEntity {
    return new BoosterTouchEntityEntity(event.name, EntityTouchStatus.FINISHED, event.lastUpdated)
  }
}
