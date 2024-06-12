import { Booster, Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CarPurchased } from '../events/car-purchased'
import { CarModel } from '../entities/car-model'
import { CarOwner } from '../entities/car-owner'

@Command({
  authorize: 'all',
})
export class BuyCar {
  public constructor(readonly id: UUID, readonly modelId: UUID, readonly ownerId: UUID) {}

  public static async handle(command: BuyCar, register: Register): Promise<void> {
    const model = await Booster.entity(CarModel, command.modelId)
    const owner = await Booster.entity(CarOwner, command.ownerId)
    register.events(new CarPurchased(command.id, owner!, model!))
  }
}
