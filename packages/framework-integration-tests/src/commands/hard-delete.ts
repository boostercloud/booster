import { Booster, Command } from '@boostercloud/framework-core'
import { EventDeleteParameters } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class HardDelete {
  public constructor(readonly entityId: string, readonly entityTypeName: string, readonly createdAt: string) {}

  public static async handle(command: HardDelete): Promise<boolean> {
    const parameters: EventDeleteParameters = {
      entityID: command.entityId,
      entityTypeName: command.entityTypeName,
      createdAt: command.createdAt,
    }
    return await Booster.deleteEvent(parameters)
  }
}
