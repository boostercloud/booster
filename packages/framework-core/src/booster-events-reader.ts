import {
  EventSearchRequest,
  BoosterConfig,
  EventSearchResponse,
  NotFoundError,
  EntityMetadata,
  InvalidParameterError,
  EventSearchParameters,
  EventParametersFilterByEntity,
  EventParametersFilterByType,
  PaginatedEventSearchResponse,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { Booster } from './booster'

export class BoosterEventsReader {
  public constructor(readonly config: BoosterConfig) {}

  public async fetch(
    eventRequest: EventSearchRequest,
    paginated = false
  ): Promise<Array<EventSearchResponse> | PaginatedEventSearchResponse> {
    await this.validateRequest(eventRequest)
    return this.processFetch(eventRequest, paginated)
  }

  private async validateRequest(eventRequest: EventSearchRequest): Promise<void> {
    const logger = getLogger(this.config, 'BoosterEventsReader#validateRequest')
    logger.debug('Validating the following event request: ', eventRequest)
    const entityMetadata = this.entityMetadataFromRequest(eventRequest)

    await entityMetadata.eventStreamAuthorizer(eventRequest.currentUser, eventRequest)
  }

  private entityMetadataFromRequest(eventRequest: EventSearchRequest): EntityMetadata {
    const { parameters } = eventRequest
    if (!isByEntitySearch(parameters) && !isByEventTypeSearch(parameters)) {
      throw new InvalidParameterError(
        'Invalid event search request. It should contain either "type" or "entity" field, but it included none'
      )
    }
    if (isByEntitySearch(parameters) && isByEventTypeSearch(parameters)) {
      throw new InvalidParameterError(
        'Invalid event search request. It should contain either "type" or "entity" field, but it included both'
      )
    }
    if (isByEntitySearch(parameters)) {
      return this.entityMetadataFromEntityName(parameters.entity)
    }
    if (isByEventTypeSearch(parameters)) {
      return this.entityMetadataFromEventName(parameters.type)
    }
    // We would never reach this point
    throw new InvalidParameterError('Could not determine event search kind')
  }

  private entityMetadataFromEntityName(entityName: string): EntityMetadata {
    const entityMetadata = this.config.entities[entityName]
    if (!entityMetadata) {
      throw new NotFoundError(`Could not find entity metadata for "${entityName}"`)
    }
    return entityMetadata
  }

  private entityMetadataFromEventName(eventName: string): EntityMetadata {
    // All the events must be reduced by an entity, so we can get the associated entity from the
    // reducers
    const reducerMetadata = this.config.reducers[eventName]
    if (!reducerMetadata) {
      throw new NotFoundError(`Could not find the entity associated to event type "${eventName}"`)
    }
    return this.entityMetadataFromEntityName(reducerMetadata.class.name)
  }

  private async processFetch(
    eventRequest: EventSearchRequest,
    paginated = false
  ): Promise<Array<EventSearchResponse> | PaginatedEventSearchResponse> {
    if (paginated) {
      return Booster.paginatedEvents(eventRequest.parameters)
    }
    return Booster.events(eventRequest.parameters)
  }
}

function isByEntitySearch(parameters: EventSearchParameters): parameters is EventParametersFilterByEntity {
  return 'entity' in parameters
}

function isByEventTypeSearch(parameters: EventSearchParameters): parameters is EventParametersFilterByType {
  return 'type' in parameters
}
