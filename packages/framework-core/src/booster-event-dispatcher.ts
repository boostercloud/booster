import {
  BoosterConfig,
  EventEnvelope,
  Logger,
  Register,
  EventHandlerInterface,
  UUID,
} from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { EventsStreamingCallback, RawEventsParser } from './services/raw-events-parser'
import { ReadModelStore } from './services/read-model-store'
import { RegisterHandler } from './booster-register-handler'
import { createInstance, Promises } from '@boostercloud/framework-common-helpers'

export class BoosterEventDispatcher {
  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param rawEvents List of RAW events from the cloud provider
   * @param config
   * @param logger
   */
  public static async dispatch(rawEvents: unknown, config: BoosterConfig, logger: Logger): Promise<void> {
    const eventStore = new EventStore(config, logger)
    const readModelStore = new ReadModelStore(config, logger)
    logger.debug('Event workflow started for raw events:', require('util').inspect(rawEvents, false, null, false))
    try {
      await RawEventsParser.streamPerEntityEvents(
        logger,
        config,
        rawEvents,
        BoosterEventDispatcher.eventProcessor(eventStore, readModelStore, logger)
      )
    } catch (err) {
      const e = err as Error
      logger.error('[BoosterEventDispatcher#dispatch] Unhandled error while dispatching event: ', e)
    }
  }

  private static eventProcessor(
    eventStore: EventStore,
    readModelStore: ReadModelStore,
    logger: Logger
  ): EventsStreamingCallback {
    return async (entityName, entityID, eventEnvelopes, config) => {
      // TODO: Separate into two independent processes the snapshotting/read-model generation process from the event handling process`
      await BoosterEventDispatcher.snapshotAndUpdateReadModels(
        entityName,
        entityID,
        eventEnvelopes,
        eventStore,
        readModelStore,
        logger
      )
      await BoosterEventDispatcher.dispatchEntityEventsToEventHandlers(eventEnvelopes, config, logger)
    }
  }

  private static async snapshotAndUpdateReadModels(
    entityName: string,
    entityID: UUID,
    envelopes: Array<EventEnvelope>,
    eventStore: EventStore,
    readModelStore: ReadModelStore,
    logger: Logger
  ): Promise<void> {
    const entitySnapshot = await eventStore.calculateAndStoreEntitySnapshot(entityName, entityID, envelopes)
    if (!entitySnapshot) {
      logger.debug(
        '[BoosterEventDispatcher#eventProcessor]: No new snapshot generated, skipping read models projection'
      )
      return
    }

    logger.debug(
      '[BoosterEventDispatcher#eventProcessor]: Snapshot loaded and started read models projection:',
      entitySnapshot
    )
    await readModelStore.project(entitySnapshot)
  }

  private static async dispatchEntityEventsToEventHandlers(
    entityEventEnvelopes: Array<EventEnvelope>,
    config: BoosterConfig,
    logger: Logger
  ): Promise<void> {
    for (const eventEnvelope of entityEventEnvelopes) {
      const eventHandlers = config.eventHandlers[eventEnvelope.typeName]
      if (!eventHandlers || eventHandlers.length == 0) {
        logger.debug(
          `[BoosterEventDispatcher#handleEvent] No event-handlers found for event ${eventEnvelope.typeName}. Skipping...`
        )
        continue
      }
      const eventClass = config.events[eventEnvelope.typeName]
      await Promises.allSettledAndFulfilled(
        eventHandlers.map(async (eventHandler: EventHandlerInterface) => {
          const eventInstance = createInstance(eventClass.class, eventEnvelope.value)
          const register = new Register(eventEnvelope.requestID, eventEnvelope.currentUser)
          logger.debug('Calling "handle" method on event handler: ', eventHandler)
          await eventHandler.handle(eventInstance, register)
          return RegisterHandler.handle(config, logger, register)
        })
      )
    }
  }
}
