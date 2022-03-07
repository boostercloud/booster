import { BoosterConfig, EventSearchParameters, EventSearchResponse, Logger } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { search } from '../helpers/query-helper'
import { buildFiltersForByFilters, buildFiltersForByTime, resultToEventSearchResponse } from './events-searcher-builder'

export async function searchEvents(
  cosmosDb: CosmosClient,
  config: BoosterConfig,
  logger: Logger,
  parameters: EventSearchParameters
): Promise<Array<EventSearchResponse>> {
  logger.debug('Initiating an events search. Filters: ', parameters)

  const eventStore = config.resourceNames.eventsStore
  const timeFilterQuery = buildFiltersForByTime(parameters.from, parameters.to)
  const eventFilterQuery = buildFiltersForByFilters(parameters)
  const filterQuery = { ...eventFilterQuery, ...timeFilterQuery, kind: { eq: 'event' } }
  const result = (await search(
    cosmosDb,
    config,
    logger,
    eventStore,
    filterQuery,
    parameters.limit,
    undefined,
    undefined,
    {
      createdAt: 'DESC',
    }
  )) as any[]
  const eventEnvelopes = resultToEventSearchResponse(result)
  logger.debug('Events search result: ', eventEnvelopes)
  return eventEnvelopes
}
