import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { CarOffers } from '../entities/car-offers'

@ReadModel({
  authorize: 'all',
})
export class CarOfferReadModel {
  public constructor(readonly id: UUID, readonly name: string, readonly purchasesIds: Array<string>) {}

  @Projects(CarOffers, 'id')
  public static projectCarOffers(offer: CarOffers, old?: CarOfferReadModel): ProjectionResult<CarOfferReadModel> {
    return new CarOfferReadModel(offer.id, offer.name, offer.purchasesIds)
  }
}
