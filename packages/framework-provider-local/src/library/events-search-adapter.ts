import {
  BoosterConfig,
  EventEnvelope,
  EventInterface,
  EventSearchParameters,
  EventSearchRequestArgs,
  EventSearchResponse,
  Logger,
  PaginatedEntitiesIdsResult,
  PaginatedEntityIdResult,
  PaginatedEventSearchResponse,
  UUID,
} from '@boostercloud/framework-types'
import { getLogger, unique } from '@boostercloud/framework-common-helpers'
import { EventRegistry } from '..'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'
import { queryRecordForWithoutValues, toLocalSortForWithoutValues } from './searcher-adapter'

const DEFAULT_CREATED_AT_SORT_ORDER = -1
const DEFAULT_KIND_FILTER = { kind: 'event' }

export async function searchEvents(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  parameters: EventSearchParameters,
  paginated = false
): Promise<Array<EventSearchResponse> | PaginatedEventSearchResponse> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEvents')
  logger.debug('Initiating an events search. Filters: ', parameters)
  const timeFilterQuery = buildFiltersForByTime(parameters.from, parameters.to)
  const eventFilterQuery = buildFiltersForByFilters(parameters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, ...DEFAULT_KIND_FILTER }
  const limit = parameters.limit

  if (paginated) {
    return paginatedSearchEvents(parameters, eventRegistry, filterQuery, limit, logger)
  }
  return listSearchEvents(eventRegistry, filterQuery, limit, logger)
}

export async function searchEntitiesIds(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  limit: number,
  afterCursor: Record<string, string> | undefined,
  entityTypeName: string
): Promise<PaginatedEntitiesIdsResult> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEntitiesIds')
  logger.debug(
    `Initiating a paginated events search. limit: ${limit}, afterCursor: ${JSON.stringify(
      afterCursor
    )}, entityTypeName: ${entityTypeName}`
  )
  const filterQuery = { ...DEFAULT_KIND_FILTER, entityTypeName: entityTypeName }

  const result = (await eventRegistry.query(filterQuery, DEFAULT_CREATED_AT_SORT_ORDER, undefined, undefined, {
    entityID: 1,
  })) as Array<PaginatedEntityIdResult>

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
  } as PaginatedEntitiesIdsResult
}

async function listSearchEvents(
  eventRegistry: EventRegistry,
  filterQuery: { createdAt?: unknown; kind: string; typeName?: string; entityTypeName?: string; entityID?: UUID },
  limit: number | undefined,
  logger: Logger
): Promise<Array<EventSearchResponse>> {
  const events = (await eventRegistry.query(filterQuery, DEFAULT_CREATED_AT_SORT_ORDER, limit)) as Array<EventEnvelope>
  const eventsSearchResponses = resultToEventSearchResponse(events)

  logger.debug('Events list search result: ', eventsSearchResponses)
  return eventsSearchResponses
}

async function paginatedSearchEvents(
  parameters: EventSearchParameters,
  eventRegistry: EventRegistry,
  filterQuery: { createdAt?: unknown; kind: string; typeName?: string; entityTypeName?: string; entityID?: UUID },
  limit: number | undefined,
  logger: Logger
): Promise<PaginatedEventSearchResponse> {
  const skipId = parameters.afterCursor?.id ? parseInt(parameters.afterCursor?.id) : 0
  const events = (await eventRegistry.query(
    filterQuery,
    DEFAULT_CREATED_AT_SORT_ORDER,
    limit,
    skipId
  )) as Array<EventEnvelope>
  const eventsSearchResponses = resultToEventSearchResponse(events)

  const paginatedResult = {
    items: eventsSearchResponses,
    count: eventsSearchResponses?.length ?? 0,
    cursor: { id: ((limit ? limit : 1) + skipId).toString() },
  }
  logger.debug('Events paginated search result: ', paginatedResult)
  return paginatedResult
}

export async function filteredSearchEvents<TEvent extends EventInterface>(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  parameters: EventSearchRequestArgs<TEvent>
): Promise<PaginatedEventSearchResponse> {
  const logger = getLogger(config, 'events-search-adapter#filteredSearchEvents')
  const queryFor = queryRecordForWithoutValues(parameters.filter)
  const query = { ...queryFor, kind: 'event' }
  logger.debug('Got query ', query)
  const skipId = parameters.afterCursor?.id ? parseInt(parameters.afterCursor?.id) : 0
  const sortByList = toLocalSortForWithoutValues(parameters.sortBy)
  const events = await eventRegistry.genericQuery(query, sortByList, parameters.limit, skipId)
  const eventsSearchResponses = resultToEventSearchResponse(events)
  logger.debug('Search result: ', eventsSearchResponses)
  return {
    items: eventsSearchResponses,
    count: eventsSearchResponses?.length ?? 0,
    cursor: { id: ((parameters.limit ? parameters.limit : 1) + skipId).toString() },
  }
}
