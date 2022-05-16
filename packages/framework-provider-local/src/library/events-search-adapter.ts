import {
  BoosterConfig,
  EventEnvelope,
  EventSearchParameters,
  EventSearchResponse,
  PaginatedEventIdResult,
  PaginatedEventsIdsResult,
} from '@boostercloud/framework-types'
import { getLogger, unique } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '..'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'

const DEFAULT_CREATED_AT_SORT_ORDER = -1
const DEFAULT_KIND_FILTER = { kind: 'event' }

export async function searchEvents(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  parameters: EventSearchParameters
): Promise<Array<EventSearchResponse>> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEvents')
  logger.debug('Initiating an events search. Filters: ', parameters)
  const timeFilterQuery = buildFiltersForByTime(parameters.from, parameters.to)
  const eventFilterQuery = buildFiltersForByFilters(parameters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, ...DEFAULT_KIND_FILTER }
  const result = (await eventRegistry.query(
    filterQuery,
    DEFAULT_CREATED_AT_SORT_ORDER,
    parameters.limit
  )) as Array<EventEnvelope>
  const eventsSearchResponses = resultToEventSearchResponse(result)
  logger.debug('Events search result: ', eventsSearchResponses)
  return eventsSearchResponses
}

export async function searchEventsIds(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  limit: number,
  afterCursor: Record<string, string> | undefined,
  entityTypeName: string
): Promise<PaginatedEventsIdsResult> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEventsIds')
  logger.debug(
    `Initiating a paginated events search. limit: ${limit}, afterCursor: ${JSON.stringify(
      afterCursor
    )}, entityTypeName: ${entityTypeName}`
  )
  const filterQuery = { ...DEFAULT_KIND_FILTER, entityTypeName: entityTypeName }

  const result = (await eventRegistry.query(filterQuery, DEFAULT_CREATED_AT_SORT_ORDER, undefined, {
    entityID: 1,
  })) as Array<PaginatedEventIdResult>

  // Nedb doesn't support DISTINCT, so we need to paginate the results manually
  const entitiesIds = result ? result?.map((v) => v.entityID) : []
  const uniqueResult = unique(entitiesIds)
  const skipId = afterCursor?.id ? parseInt(afterCursor?.id) : 0
  const paginated = uniqueResult.slice(skipId, skipId + limit)
  const paginatedResult = paginated.map((v) => ({ entityID: v }))
  logger.debug('Unique events search result', paginatedResult)
  return {
    items: paginatedResult,
    count: paginatedResult?.length ?? 0,
    cursor: { id: ((limit ? limit : 1) + skipId).toString() },
  } as PaginatedEventsIdsResult
}
