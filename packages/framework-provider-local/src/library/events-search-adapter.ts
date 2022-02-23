import { Logger, BoosterConfig, EventFilter, EventSearchResponse } from '@boostercloud/framework-types'
import { EventRegistry } from '..'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'

export async function searchEvents(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  logger: Logger,
  filters: EventFilter
): Promise<Array<EventSearchResponse>> {
  logger.debug('Initiating an events search. Filters: ', filters)
  const timeFilterQuery = buildFiltersForByTime(filters.from, filters.to)
  const eventFilterQuery = buildFiltersForByFilters(filters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, kind: 'event' }
  const result = await eventRegistry.query(filterQuery, -1)
  const eventsSearchResponses = resultToEventSearchResponse(result)
  logger.debug('Events search result: ', eventsSearchResponses)
  return eventsSearchResponses
}
