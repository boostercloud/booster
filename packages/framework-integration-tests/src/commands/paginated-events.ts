import { Booster, Command } from '@boostercloud/framework-core'
import { EventSearchParameters, PaginatedEventSearchResponse, Register } from '@boostercloud/framework-types'

@Command({
  authorize: 'all',
})
export class PaginatedEvents {
  constructor(readonly entity: string, readonly entityID: string) {}

  public static async handle(
    command: PaginatedEvents,
    register: Register
  ): Promise<Array<PaginatedEventSearchResponse>> {
    let cursor: Record<string, string> | undefined = undefined
    let count = 0
    const results: Array<PaginatedEventSearchResponse> = []
    do {
      const request: EventSearchParameters = {
        entity: command.entity,
        entityID: command.entityID,
        limit: 10,
        afterCursor: cursor,
      }
      const paginatedEventSearchResponse: PaginatedEventSearchResponse = await Booster.paginatedEvents(request)
      count = paginatedEventSearchResponse.count ?? 0
      cursor = paginatedEventSearchResponse.cursor
      results.push(paginatedEventSearchResponse)
    } while (count !== 0 && cursor !== undefined)
    return results
  }
}
