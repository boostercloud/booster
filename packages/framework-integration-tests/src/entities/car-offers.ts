import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CarOfferAdded } from '../events/car-offer-added'

@Entity({
  authorizeReadEvents: 'all',
})
export class CarOffers {
  public constructor(readonly id: UUID, readonly name: string, readonly purchasesIds: Array<string>) {}
  public getId() {
    return this.id
  }
  @Reduces(CarOfferAdded)
  public static reduceCarOfferAdded(event: CarOfferAdded, currentCarDriver: CarOffers): CarOffers {
    return new CarOffers(event.id, event.name, event.purchasesIds)
  }
}
