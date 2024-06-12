import { Event } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CarOwner } from '../entities/car-owner'
import { CarModel } from '../entities/car-model'

@Event
export class CarPurchased {
  public constructor(readonly id: UUID, readonly owner: CarOwner, readonly model: CarModel) {}

  public entityID(): UUID {
    return this.id
  }
}
