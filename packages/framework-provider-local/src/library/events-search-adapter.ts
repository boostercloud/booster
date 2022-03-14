import { Logger, BoosterConfig, EventSearchParameters, EventSearchResponse } from '@boostercloud/framework-types'
import { EventRegistry } from '..'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'

export async function searchEvents(
  eventRegistry: EventRegistry,
  config: BoosterConfig,
  logger: Logger,
  parameters: EventSearchParameters
): Promise<Array<EventSearchResponse>> {
  logger.debug('Initiating an events search. Filters: ', parameters)
  const timeFilterQuery = buildFiltersForByTime(parameters.from, parameters.to)
  const eventFilterQuery = buildFiltersForByFilters(parameters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, kind: 'event' }
  const result = await eventRegistry.query(filterQuery, -1, parameters.limit)
  const eventsSearchResponses = resultToEventSearchResponse(result)
  logger.debug('Events search result: ', eventsSearchResponses)
  return eventsSearchResponses
}
