import { Projects, ReadModel } from '@boostercloud/framework-core'
import { FilterFor, ProjectionResult, ReadModelAction, UUID } from '@boostercloud/framework-types'
import { CarOwner } from '../entities/car-owner'
import { CarModel } from '../entities/car-model'
import { CarPurchase } from '../entities/car-purchase'
import { CarOffers } from '../entities/car-offers'

/**
 * CardModel neither CarOwner have the CarModelOwnerReadModel id as a field,
 * so we need to use a ReadModel JoinKey to get all the CarOwnerModelReadModel instances for a given CarOwner or CarModel
 */
@ReadModel({
  authorize: 'all',
})
export class CarPurchasesReadModel {
  public constructor(
    readonly id: UUID,
    readonly carModel?: CarModel,
    readonly carOwner?: CarOwner,
    readonly offers?: Array<CarOffers>
  ) {}

  @Projects(CarPurchase, 'id')
  public static projectWithPurchase(
    purchase: CarPurchase,
    oldCarPurchaseReadModel?: CarPurchasesReadModel
  ): ProjectionResult<CarPurchasesReadModel> {
    return new CarPurchasesReadModel(purchase.id, purchase.model, purchase.owner, oldCarPurchaseReadModel?.offers)
  }

  // A ReadModel JoinKey to get all the CarOwnerModelReadModel instances for a given CarOwner
  @Projects(CarOwner, (carOwner: CarOwner): FilterFor<CarPurchasesReadModel> | undefined => {
    if (carOwner.name === 'SKIP') {
      return
    }
    return {
      carOwner: {
        id: {
          eq: carOwner.id,
        },
      },
    }
  })
  public static projectWithOwner(
    owner: CarOwner,
    readModelId: UUID | undefined,
    oldCarPurchaseReadModel?: CarPurchasesReadModel
  ): ProjectionResult<CarPurchasesReadModel> {
    if (!readModelId) {
      return ReadModelAction.Nothing
    }
    return new CarPurchasesReadModel(
      readModelId,
      oldCarPurchaseReadModel?.carModel,
      owner,
      oldCarPurchaseReadModel?.offers
    )
  }

  // A ReadModel JoinKey to get all the CarOwnerModelReadModel instances for a given CarModel
  @Projects(CarModel, (carModel: CarModel): FilterFor<CarPurchasesReadModel> => {
    return {
      carModel: {
        id: {
          eq: carModel.id,
        },
      },
    }
  })
  public static projectWithModel(
    model: CarModel,
    readModelId: UUID | undefined,
    oldCarPurchaseReadModel?: CarPurchasesReadModel
  ): ProjectionResult<CarPurchasesReadModel> {
    if (!readModelId) {
      return ReadModelAction.Nothing
    }
    return new CarPurchasesReadModel(
      readModelId,
      model,
      oldCarPurchaseReadModel?.carOwner,
      oldCarPurchaseReadModel?.offers
    )
  }

  @Projects(CarOffers, 'purchasesIds')
  public static projectWithOffer(
    carOffers: CarOffers,
    readModelId: UUID,
    oldCarPurchaseReadModel?: CarPurchasesReadModel
  ): ProjectionResult<CarPurchasesReadModel> {
    if (!oldCarPurchaseReadModel) {
      return ReadModelAction.Nothing
    }
    console.log(`Updating purchase ${readModelId} with offers ${JSON.stringify(carOffers)}`)

    let offers = oldCarPurchaseReadModel?.offers || []
    if (offers.find((existingOfferId) => existingOfferId.id == carOffers.id)) {
      offers = offers.map((existingOffer) => (existingOffer.id == carOffers.id ? carOffers : existingOffer))
    } else {
      offers.push(carOffers)
    }
    return new CarPurchasesReadModel(
      oldCarPurchaseReadModel.id,
      oldCarPurchaseReadModel?.carModel,
      oldCarPurchaseReadModel?.carOwner,
      offers
    )
  }
}
