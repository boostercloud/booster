import {
  EventSearchRequest,
  BoosterConfig,
  Logger,
  NotAuthorizedError,
  EventSearchResponse,
  NotFoundError,
  EntityMetadata,
  InvalidParameterError,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { Booster } from './booster'

export class BoosterEventReader {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

  public async fetch(eventRequest: EventSearchRequest): Promise<Array<EventSearchResponse>> {
    this.validateRequest(eventRequest)
    return this.processFetch(eventRequest)
  }

  private validateRequest(eventRequest: EventSearchRequest): void {
    this.logger.debug('Validating the following event request: ', eventRequest)
    const entityMetadata = this.entityMetadataFromRequest(eventRequest)

    if (!BoosterAuth.isUserAuthorized(entityMetadata.authorizeReadEvents, eventRequest.currentUser)) {
      throw new NotAuthorizedError('Access denied for reading events')
    }
  }

  private entityMetadataFromRequest(eventRequest: EventSearchRequest): EntityMetadata {
    if ('entity' in eventRequest.filters) {
      return this.entityMetadataFromEntityName(eventRequest.filters.entity)
    }
    if ('type' in eventRequest.filters) {
      return this.entityMetadataFromEventName(eventRequest.filters.type)
    }
    throw new InvalidParameterError('Invalid event search request. It should contain either "type" or "entity" field')
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

  private async processFetch(eventRequest: EventSearchRequest): Promise<Array<EventSearchResponse>> {
    return Booster.events(eventRequest.filters)
  }
}
