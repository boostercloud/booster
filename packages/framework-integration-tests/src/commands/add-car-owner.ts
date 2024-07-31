import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CarOwnerAdded } from '../events/car-owner-added'

@Command({
  authorize: 'all',
})
export class AddCarOwner {
  public constructor(readonly id: UUID, readonly name: string) {}

  public static async handle(command: AddCarOwner, register: Register): Promise<void> {
    register.events(new CarOwnerAdded(command.id, command.name))
  }
}
