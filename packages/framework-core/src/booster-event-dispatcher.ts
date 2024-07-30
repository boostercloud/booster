import { BoosterConfig, TraceActionTypes } from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { RawEventsParser } from './services/raw-events-parser'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { ReadModelStore } from './services/read-model-store'
import { Trace } from './instrumentation'
import { BoosterEventProcessor } from './booster-event-processor'

export class BoosterEventDispatcher {
  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param rawEvents List of raw events from the cloud provider
   * @param config
   */
  @Trace(TraceActionTypes.DISPATCH_EVENTS)
  public static async dispatch(rawEvents: unknown, config: BoosterConfig): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher#dispatch')
    const eventStore = new EventStore(config)
    const readModelStore = new ReadModelStore(config)
    logger.debug('Event workflow started for raw events:', require('util').inspect(rawEvents, false, null, false))
    try {
      const eventEnvelopes = config.provider.events.rawToEnvelopes(rawEvents)
      await RawEventsParser.streamPerEntityEvents(
        config,
        eventEnvelopes,
        BoosterEventProcessor.eventProcessor(eventStore, readModelStore)
      )
    } catch (e) {
      logger.error('Unhandled error while dispatching event: ', e)
    }
  }
}
