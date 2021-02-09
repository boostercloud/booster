import {
  EventSearchRequest,
  BoosterConfig,
  Logger,
  NotAuthorizedError,
  EventSearchResponse,
  NotFoundError,
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

    if ('entity' in eventRequest.filters && !this.config.entities[eventRequest.filters.entity]) {
      throw new NotFoundError(`Could not find entity "${eventRequest.filters.entity}"`)
    }

    if ('type' in eventRequest.filters && !this.config.reducers[eventRequest.filters.type]) {
      throw new NotFoundError(`Could not find event type "${eventRequest.filters.type}"`)
    }

    if (!BoosterAuth.isUserAuthorized(this.config.authorizeReadEvents, eventRequest.currentUser)) {
      throw new NotAuthorizedError('Access denied for reading events')
    }
  }

  private async processFetch(eventRequest: EventSearchRequest): Promise<Array<EventSearchResponse>> {
    return Booster.events(eventRequest.filters)
  }
}
