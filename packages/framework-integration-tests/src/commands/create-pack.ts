import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { UserWithEmail, Admin } from '../roles'
import { PackCreated } from '../events/pack-created'

@Command({
  authorize: [Admin, UserWithEmail],
})
export class CreatePack {
  public constructor(readonly name: string, readonly products: Array<UUID>, readonly packID?: UUID) {}

  public static async handle(command: CreatePack, register: Register): Promise<void> {
    const packID = command.packID ?? UUID.generate()
    register.events(new PackCreated(packID, command.name, command.products))
  }
}
