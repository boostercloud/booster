import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { CarModelNameUpdated } from '../events/car-model-name-updated'

@Command({
  authorize: 'all',
})
export class UpdateCarModelName {
  public constructor(readonly id: UUID, readonly name: string) {}

  public static async handle(command: UpdateCarModelName, register: Register): Promise<void> {
    register.events(new CarModelNameUpdated(command.id, command.name))
  }
}
