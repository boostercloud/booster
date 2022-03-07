import { BoosterConfig, EventFilter, EventSearchResponse, Logger } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { search } from '../helpers/query-helper'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'

export async function searchEvents(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  filters: EventFilter,
  limit?: number
): Promise<Array<EventSearchResponse>> {
  logger.debug('Initiating an events search. Filters: ', filters)

  const eventStore = config.resourceNames.eventsStore
  const timeFilterQuery = buildFiltersForByTime(filters.from, filters.to)
  const eventFilterQuery = buildFiltersForByFilters(filters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, kind: { eq: 'event' } }
  const result = (await search(cosmosDb, config, logger, eventStore, filterQuery, limit, undefined, undefined, {
    createdAt: 'DESC',
  })) as any[]
  const eventEnvelopes = resultToEventSearchResponse(result)
  logger.debug('Events search result: ', eventEnvelopes)
  return eventEnvelopes
}
