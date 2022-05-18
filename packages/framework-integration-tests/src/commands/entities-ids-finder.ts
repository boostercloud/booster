import { Booster, Command } from '@boostercloud/framework-core'
import { PaginatedEventsIdsResult, Register } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class EntitiesIdsFinder {
  constructor(
    readonly entityName: string,
    readonly limit: number,
    readonly afterCursor?: Record<string, string>
  ) {}

  public static async handle(command: EntitiesIdsFinder, register: Register): Promise<PaginatedEventsIdsResult> {
    return await Booster.eventsIds(command.entityName, command.limit, command.afterCursor)
  }
}
