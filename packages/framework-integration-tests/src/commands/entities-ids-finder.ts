import { Booster, Command } from '@boostercloud/framework-core'
import { PaginatedEntitiesIdsResult, Register } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class EntitiesIdsFinder {
  constructor(readonly entityName: string, readonly limit: number, readonly afterCursor?: Record<string, string>) {}

  public static async handle(command: EntitiesIdsFinder, register: Register): Promise<PaginatedEntitiesIdsResult> {
    return await Booster.entitiesIDs(command.entityName, command.limit, command.afterCursor)
  }
}
