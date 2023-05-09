import {
  BoosterConfig,
  EventEnvelope,
  EventHandlerGlobalError,
  EventHandlerInterface,
  EventInterface,
  Register,
  UUID,
} from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { EventsStreamingCallback, RawEventsParser } from './services/raw-events-parser'
import { ReadModelStore } from './services/read-model-store'
import { RegisterHandler } from './booster-register-handler'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { createInstance, getLogger, Promises } from '@boostercloud/framework-common-helpers'
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

  /**
   * Builds a function that will be called once for each entity from the `RawEventsParser.streamPerEntityEvents` method
   * after the page of events is grouped by entity.
   */
  private static eventProcessor(eventStore: EventStore, readModelStore: ReadModelStore): EventsStreamingCallback {
    return async (entityName, entityID, eventEnvelopes, config) => {
      const eventEnvelopesProcessors = [
        BoosterEventDispatcher.dispatchEntityEventsToEventHandlers(eventEnvelopes, config),
      ]

      // Read models are not updated for notification events (events that are not related to an entity but a topic)
      if (!(entityName in config.topicToEvent)) {
        eventEnvelopesProcessors.push(
          BoosterEventDispatcher.snapshotAndUpdateReadModels(config, entityName, entityID, eventStore, readModelStore)
        )
      }

      await Promises.allSettledAndFulfilled(eventEnvelopesProcessors)
    }
  }

  private static async snapshotAndUpdateReadModels(
    config: BoosterConfig,
    entityName: string,
    entityID: UUID,
    eventStore: EventStore,
    readModelStore: ReadModelStore
  ): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher#snapshotAndUpdateReadModels')
    let entitySnapshot = undefined
    try {
      entitySnapshot = await eventStore.fetchEntitySnapshot(entityName, entityID)
    } catch (e) {
      logger.error('Error while fetching or reducing entity snapshot:', e)
    }
    if (!entitySnapshot) {
      logger.debug('No new snapshot generated, skipping read models projection')
      return
    }
    logger.debug('Snapshot loaded and started read models projection:', entitySnapshot)
    await readModelStore.project(entitySnapshot)
  }

  private static async dispatchEntityEventsToEventHandlers(
    entityEventEnvelopes: Array<EventEnvelope | NotificationInterface>,
    config: BoosterConfig
  ): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher.dispatchEntityEventsToEventHandlers')
    for (const eventEnvelope of entityEventEnvelopes) {
      const eventHandlers = config.eventHandlers[eventEnvelope.typeName]
      const globalEventHandler = config.globalEventHandler
      if ((!eventHandlers || eventHandlers.length == 0) && !globalEventHandler) {
        logger.debug(`No event-handlers found for event ${eventEnvelope.typeName}. Skipping...`)
        continue
      }
      const eventInstance = this.getEventInstance(config, eventEnvelope)
      if (eventHandlers && eventHandlers.length > 0) {
        await Promises.allSettledAndFulfilled(
          eventHandlers.map(async (eventHandler: EventHandlerInterface) => {
            logger.debug('Calling "handle" method on event handler: ', eventHandler)
            await this.callEventHandler(eventHandler, eventInstance, eventEnvelope, config)
          })
        )
      }
      if (globalEventHandler) {
        logger.debug('Calling "handle" method on global event handler')
        await this.callEventHandler(globalEventHandler, eventInstance, eventEnvelope, config)
      }
    }
  }

  private static getEventInstance(
    config: BoosterConfig,
    eventEnvelope: EventEnvelope | NotificationInterface
  ): EventInterface {
    const eventClass = config.events[eventEnvelope.typeName] ?? config.notifications[eventEnvelope.typeName]
    return createInstance(eventClass.class, eventEnvelope.value)
  }

  private static async callEventHandler(
    eventHandler: EventHandlerInterface,
    eventInstance: EventInterface,
    eventEnvelope: EventEnvelope | NotificationInterface,
    config: BoosterConfig
  ): Promise<void> {
    const register = new Register(eventEnvelope.requestID, {}, RegisterHandler.flush, eventEnvelope.currentUser)
    try {
      await eventHandler.handle(eventInstance, register)
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
      const error = await globalErrorDispatcher.dispatch(new EventHandlerGlobalError(eventInstance, e))
      if (error) throw error
    }
    await RegisterHandler.handle(config, register)
  }
}
