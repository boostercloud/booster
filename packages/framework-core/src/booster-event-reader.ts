import {
  EventEnvelope,
  EventRequestEnvelope,
  BoosterConfig,
  Logger,
  NotAuthorizedError,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'

export class BoosterEventReader {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

  public async fetch(eventRequest: EventRequestEnvelope): Promise<Array<EventEnvelope>> {
    this.validateRequest(eventRequest)
    return this.processFetch(eventRequest)
  }

  private validateRequest(eventRequest: EventRequestEnvelope): void {
    this.logger.debug('Validating the following event request: ', eventRequest)

    if (!BoosterAuth.isUserAuthorized(this.config.authorizeReadEvents, eventRequest.currentUser)) {
      throw new NotAuthorizedError('Access denied for reading events')
    }
  }

  private async processFetch(eventRequest: EventRequestEnvelope): Promise<Array<EventEnvelope>> {
    // TODO: Create the searcher and search for the events
    // TODO: Create the conversion for the event envelope to EventResult
    // TODO: Do the actual search in AWS
    return []
  }
}
