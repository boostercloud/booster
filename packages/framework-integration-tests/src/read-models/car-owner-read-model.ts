import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { CarOwner } from '../entities/car-owner'

@ReadModel({
  authorize: 'all',
})
export class CarOwnerReadModel {
  public constructor(readonly id: UUID, readonly name: string) {}

  @Projects(CarOwner, 'id')
  public static projectCarOwner(owner: CarOwner, old?: CarOwnerReadModel): ProjectionResult<CarOwnerReadModel> {
    return new CarOwnerReadModel(owner.id, owner.name)
  }
}
