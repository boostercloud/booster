import {
  BoosterConfig,
  EventSearchParameters,
  EventSearchResponse,
  FilterFor,
  Logger,
  PaginatedEntitiesIdsResult,
  PaginatedEventSearchResponse,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { CosmosClient } from '@azure/cosmos'
import { search } from '../helpers/query-helper'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'

export async function searchEvents(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  parameters: EventSearchParameters,
  paginated = false
): Promise<Array<EventSearchResponse> | PaginatedEventSearchResponse> {
  const logger = getLogger(config, 'events-searcher-adapter#searchEvents')
  logger.debug('Initiating an events search. Filters: ', parameters)

  const eventStore = config.resourceNames.eventsStore
  const timeFilterQuery = buildFiltersForByTime(parameters.from, parameters.to)
  const eventFilterQuery = buildFiltersForByFilters(parameters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, kind: { eq: 'event' } }

  if (paginated) {
    return paginatedSearchEvents(cosmosDb, config, eventStore, filterQuery, parameters, logger)
  }
  return listSearchEvents(cosmosDb, config, eventStore, filterQuery, parameters, logger)
}

export async function searchEntitiesIds(
  cosmosDb: CosmosClient,
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
  const filterQuery = { kind: { eq: 'event' }, entityTypeName: { eq: entityTypeName } }
  const eventStore = config.resourceNames.eventsStore

  const result = (await search(
    cosmosDb,
    config,
    eventStore,
    filterQuery,
    limit,
    afterCursor,
    true,
    undefined,
    'DISTINCT c.entityID'
  )) as PaginatedEntitiesIdsResult
  logger.debug('Unique events search result', result)
  return result
}

async function listSearchEvents(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  eventStore: string,
  filterQuery: FilterFor<unknown>,
  parameters: EventSearchParameters,
  logger: Logger
): Promise<Array<EventSearchResponse>> {
  const events = (await search(cosmosDb, config, eventStore, filterQuery, parameters.limit, undefined, undefined, {
    createdAt: 'DESC',
  })) as any[]
  const eventEnvelopes = resultToEventSearchResponse(events)
  logger.debug('Events list search result: ', eventEnvelopes)
  return eventEnvelopes
}

async function paginatedSearchEvents(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  eventStore: string,
  filterQuery: FilterFor<unknown>,
  parameters: EventSearchParameters,
  logger: Logger
): Promise<PaginatedEventSearchResponse> {
  const limit = parameters.limit
  const afterCursor = parameters.afterCursor
  const paginatedEvents = (await search(cosmosDb, config, eventStore, filterQuery, limit, afterCursor, true, {
    createdAt: 'DESC',
  })) as PaginatedEventSearchResponse

  const result = {
    ...paginatedEvents,
    items: resultToEventSearchResponse(paginatedEvents.items),
  }
  logger.debug('Events paginated search result: ', result)
  return result
}
