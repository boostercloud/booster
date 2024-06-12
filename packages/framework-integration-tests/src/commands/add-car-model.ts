import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CarModelAdded } from '../events/car-model-added'

@Command({
  authorize: 'all',
})
export class AddCarModel {
  public constructor(readonly id: UUID, readonly name: string, readonly brand: string) {}

  public static async handle(command: AddCarModel, register: Register): Promise<void> {
    register.events(new CarModelAdded(command.id, command.name, command.brand))
  }
}
