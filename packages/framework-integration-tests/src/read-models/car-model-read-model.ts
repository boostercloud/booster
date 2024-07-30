import { Projects, ReadModel } from '@boostercloud/framework-core'
import { ProjectionResult, UUID } from '@boostercloud/framework-types'
import { CarModel } from '../entities/car-model'

@ReadModel({
  authorize: 'all',
})
export class CarModelReadModel {
  public constructor(readonly id: UUID, readonly name: string, readonly brand: string) {}

  @Projects(CarModel, 'id')
  public static projectCarModel(model: CarModel, old?: CarModelReadModel): ProjectionResult<CarModelReadModel> {
    return new CarModelReadModel(model.id, model.name, model.brand)
  }
}
