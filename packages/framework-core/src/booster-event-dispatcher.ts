import { BoosterConfig, EventEnvelope, Logger, Register, EventInterface } from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { RawEventsParser } from './services/raw-events-parser'
import { ReadModelStore } from './services/read-model-store'
import { RegisterHandler } from './booster-register-handler'
import { EventHandlerInterface } from '@boostercloud/framework-types'

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
      await RawEventsParser.streamEvents(
        config,
        rawEvents,
        BoosterEventDispatcher.eventProcessor(eventStore, readModelStore, logger)
      )
    } catch (e) {
      logger.error('[BoosterEventDispatcher#dispatch] Unhandled error while dispatching event: ', e)
    }
  }

  private static eventProcessor(
    eventStore: EventStore,
    readModelStore: ReadModelStore,
    logger: Logger
  ): (event: EventEnvelope, config: BoosterConfig) => Promise<void> {
    return async (eventEnvelope, config) => {
      logger.debug('[BoosterEventDispatcher#eventProcessor]: Started processing workflow for event:', eventEnvelope)
      await eventStore.append(eventEnvelope)

      // TODO: Separate into two independent processes the snapshotting/read-model generation process from the event handling process
      await Promise.all([
        BoosterEventDispatcher.snapshotAndUpdateReadModels(eventEnvelope, eventStore, readModelStore, logger),
        BoosterEventDispatcher.handleEvent(eventEnvelope, config, logger),
      ])
    }
  }

  private static async snapshotAndUpdateReadModels(
    event: EventEnvelope,
    eventStore: EventStore,
    readModelStore: ReadModelStore,
    logger: Logger
  ): Promise<void> {
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

  private static async handleEvent(eventEnvelope: EventEnvelope, config: BoosterConfig, logger: Logger): Promise<void> {
    await Promise.all(
      config.eventHandlers[eventEnvelope.typeName].map((eventHandler: EventHandlerInterface) => {
        const register = new Register(eventEnvelope.requestID, eventEnvelope.currentUser)
        logger.debug('Calling "handle" method on event handler: ', eventHandler)
        eventHandler.handle(eventEnvelope.value as EventInterface, register)
        return RegisterHandler.handle(config, logger, register)
      })
    )
  }
}
