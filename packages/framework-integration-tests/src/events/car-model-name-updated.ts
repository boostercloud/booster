import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'

@Event
export class CarModelNameUpdated {
  public constructor(readonly carModelId: UUID, readonly newModelName: string) {}

  public entityID(): UUID {
    return this.carModelId
  }
}
