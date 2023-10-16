import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CarOwnerAdded } from '../events/car-owner-added'
import { CarOwnerNameUpdated } from '../events/car-owner-name-updated'

@Entity({
  authorizeReadEvents: 'all',
})
export class CarOwner {
  public constructor(readonly id: UUID, public name: string) {}
  public getId() {
    return this.id
  }
  @Reduces(CarOwnerAdded)
  public static addOwner(event: CarOwnerAdded, currentCarOwner: CarOwner): CarOwner {
    return new CarOwner(event.id, event.name)
  }

  @Reduces(CarOwnerNameUpdated)
  public static updateName(event: CarOwnerNameUpdated, currentCarOwner: CarOwner): CarOwner {
    if (!currentCarOwner) {
      throw new Error('CarOwner not found')
    }
    currentCarOwner.name = event.newOwnerName
    return currentCarOwner
  }
}
