import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CarOwnerNameUpdated } from '../events/car-owner-name-updated'

@Command({
  authorize: 'all',
})
export class UpdateCarOwnerName {
  public constructor(readonly id: UUID, readonly name: string) {}

  public static async handle(command: UpdateCarOwnerName, register: Register): Promise<void> {
    register.events(new CarOwnerNameUpdated(command.id, command.name))
  }
}
