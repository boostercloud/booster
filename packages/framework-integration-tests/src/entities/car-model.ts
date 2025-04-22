import { Entity, Reduces } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CarModelAdded } from '../events/car-model-added'
import { CarModelNameUpdated } from '../events/car-model-name-updated'

@Entity({
  authorizeReadEvents: 'all',
})
export class CarModel {
  public constructor(readonly id: UUID, public name: string, readonly brand: string) {}
  public getId() {
    return this.id
  }
  @Reduces(CarModelAdded)
  public static reduceCarModelAdded(event: CarModelAdded, currentCarModel: CarModel): CarModel {
    return new CarModel(event.id, event.name, event.brand)
  }

  @Reduces(CarModelNameUpdated)
  public static reduceCarModelNameUpdated(event: CarModelNameUpdated, currentCarModel: CarModel): CarModel {
    if (!currentCarModel) {
      throw new Error('CarModel not found')
    }
    currentCarModel.name = event.newModelName
    return currentCarModel
  }
}
