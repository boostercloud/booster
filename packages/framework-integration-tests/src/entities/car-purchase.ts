import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CarPurchased } from '../events/car-purchased'
import { CarModel } from './car-model'
import { CarOwner } from './car-owner'

@Entity({
  authorizeReadEvents: 'all',
})
export class CarPurchase {
  public constructor(readonly id: UUID, readonly model: CarModel, readonly owner: CarOwner) {}
  public getId() {
    return this.id
  }
  @Reduces(CarPurchased)
  public static reduceCarPurchase(event: CarPurchased, currentCarPurchase: CarPurchase): CarPurchase {
    return new CarPurchase(event.id, event.model, event.owner)
  }
}
