import { BoosterConfig, EventEnvelope, Logger } from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { RawEventsParser } from './services/raw-events-parser'
import { ReadModelStore } from './services/read-model-store'

export class BoosterEventDispatcher {
  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param rawEvents List of RAW events from the cloud provider
   * @param config
   * @param logger
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public static async dispatch(rawEvents: any, config: BoosterConfig, logger: Logger): Promise<void> {
    const eventStore = new EventStore(config, logger)
    const readModelStore = new ReadModelStore(config, logger)
    logger.debug('Event workflow started for RAW events:', rawEvents)
    try {
      await RawEventsParser.streamEvents(config, rawEvents, this.eventProcessor(eventStore, readModelStore, logger))
    } catch (e) {
      logger.error('[BoosterEventDispatcher#dispatch] Unhandled error while dispatching event: ', e)
    }
  }

  private static eventProcessor(
    eventStore: EventStore,
    readModelStore: ReadModelStore,
    logger: Logger
  ): (event: EventEnvelope) => Promise<void> {
    return async (event) => {
      logger.debug('[BoosterEventDispatcher#eventProcessor]: Started processing workflow for event:', event)
      await eventStore.append(event)
      const entitySnapshot = await eventStore.fetchEntitySnapshot(event.entityTypeName, event.entityID)
      if (entitySnapshot) {
        logger.debug(
          '[BoosterEventDispatcher#eventProcessor]: Snapshot loaded and started read models projection:',
          entitySnapshot
        )
        await readModelStore.project(entitySnapshot)
      } else {
        logger.debug(
          '[BoosterEventDispatcher#eventProcessor]: No new snapshot generated, skipping read models projection'
        )
      }
    }
  }
}
