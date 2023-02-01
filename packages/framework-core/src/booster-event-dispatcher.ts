import {
  BoosterConfig,
  EventEnvelope,
  Register,
  EventHandlerInterface,
  UUID,
  EventHandlerGlobalError,
} from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { EventsStreamingCallback, RawEventsParser } from './services/raw-events-parser'
import { ReadModelStore } from './services/read-model-store'
import { RegisterHandler } from './booster-register-handler'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { createInstance, Promises, getLogger } from '@boostercloud/framework-common-helpers'
import { NotificationInterface } from 'framework-types/dist'

export class BoosterEventDispatcher {
  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param rawEvents List of raw events from the cloud provider
   * @param config
   * @param logger
   */
  public static async dispatch(rawEvents: unknown, config: BoosterConfig): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher#dispatch')
    const eventStore = new EventStore(config)
    const readModelStore = new ReadModelStore(config)
    logger.debug('Event workflow started for raw events:', require('util').inspect(rawEvents, false, null, false))
    try {
      await RawEventsParser.streamPerEntityEvents(
        config,
        rawEvents,
        BoosterEventDispatcher.eventProcessor(eventStore, readModelStore)
      )
    } catch (e) {
      logger.error('Unhandled error while dispatching event: ', e)
    }
  }

  private static eventProcessor(eventStore: EventStore, readModelStore: ReadModelStore): EventsStreamingCallback {
    return async (entityName, entityID, eventEnvelopes, config) => {
      if (!(entityName in config.topicToEvent)) {
        // TODO: Separate into two independent processes the snapshotting/read-model generation process from the event handling process`
        await BoosterEventDispatcher.snapshotAndUpdateReadModels(
          config,
          entityName,
          entityID,
          eventEnvelopes,
          eventStore,
          readModelStore
        )
      }
      await BoosterEventDispatcher.dispatchEntityEventsToEventHandlers(eventEnvelopes, config)
    }
  }

  private static async snapshotAndUpdateReadModels(
    config: BoosterConfig,
    entityName: string,
    entityID: UUID,
    envelopes: Array<EventEnvelope>,
    eventStore: EventStore,
    readModelStore: ReadModelStore
  ): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher#snapshotAndUpdateReadModels')
    const snapshotsForIndependentEntities = await eventStore.calculateAndStoreEntitySnapshot(
      entityName,
      entityID,
      envelopes
    )
    if (snapshotsForIndependentEntities.length === 0) {
      logger.debug('No new snapshots generated, skipping read models projection')
      return
    }

    logger.debug('Snapshot(s) calculated and started read models projection:', snapshotsForIndependentEntities)

    const projectedPromises = snapshotsForIndependentEntities.map((entitySnapshot) =>
      readModelStore.project(entitySnapshot)
    )
    await Promises.allSettledAndFulfilled(projectedPromises)
  }

  private static async dispatchEntityEventsToEventHandlers(
    entityEventEnvelopes: Array<EventEnvelope | NotificationInterface>,
    config: BoosterConfig
  ): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher.dispatchEntityEventsToEventHandlers')
    for (const eventEnvelope of entityEventEnvelopes) {
      const eventHandlers = config.eventHandlers[eventEnvelope.typeName]
      if (!eventHandlers || eventHandlers.length == 0) {
        logger.debug(`No event-handlers found for event ${eventEnvelope.typeName}. Skipping...`)
        continue
      }
      const eventClass = config.events[eventEnvelope.typeName] ?? config.notifications[eventEnvelope.typeName]
      await Promises.allSettledAndFulfilled(
        eventHandlers.map(async (eventHandler: EventHandlerInterface) => {
          const eventInstance = createInstance(eventClass.class, eventEnvelope.value)
          const register = new Register(eventEnvelope.requestID, {}, RegisterHandler.flush, eventEnvelope.currentUser)
          logger.debug('Calling "handle" method on event handler: ', eventHandler)
          try {
            await eventHandler.handle(eventInstance, register)
          } catch (e) {
            const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
            const error = await globalErrorDispatcher.dispatch(new EventHandlerGlobalError(eventInstance, e))
            if (error) throw error
          }
          return RegisterHandler.handle(config, register)
        })
      )
    }
  }
}
