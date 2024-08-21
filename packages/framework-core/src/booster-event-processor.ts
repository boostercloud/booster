import {
  BoosterConfig,
  EventEnvelope,
  EventHandlerGlobalError,
  EventHandlerInterface,
  EventInterface,
  Register,
  TraceActionTypes,
  UUID,
} from '@boostercloud/framework-types'
import { EventStore } from './services/event-store'
import { EventsStreamingCallback } from './services/raw-events-parser'
import { ReadModelStore } from './services/read-model-store'
import { RegisterHandler } from './booster-register-handler'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { createInstance, getLogger, Promises } from '@boostercloud/framework-common-helpers'
import { NotificationInterface } from 'framework-types/dist'
import { Trace } from './instrumentation'
import { BOOSTER_GLOBAL_EVENT_HANDLERS } from './decorators'

export class BoosterEventProcessor {
  /**
   * Function that will be called once for each entity from the `RawEventsParser.streamPerEntityEvents` method
   * after the page of events is grouped by entity.
   */
  public static eventProcessor(eventStore: EventStore, readModelStore: ReadModelStore): EventsStreamingCallback {
    return async (entityName, entityID, eventEnvelopes, config) => {
      // Filter events that have already been dispatched
      const eventsNotDispatched = await BoosterEventProcessor.filterDispatched(config, eventEnvelopes, eventStore)
      const eventEnvelopesProcessors = [
        BoosterEventProcessor.dispatchEntityEventsToEventHandlers(eventsNotDispatched, config),
      ]

      // Read models are not updated for notification events (events that are not related to an entity but a topic)
      if (!(entityName in config.topicToEvent)) {
        eventEnvelopesProcessors.push(
          BoosterEventProcessor.snapshotAndUpdateReadModels(config, entityName, entityID, eventStore, readModelStore)
        )
      }

      await Promises.allSettledAndFulfilled(eventEnvelopesProcessors)
    }
  }

  private static async filterDispatched(
    config: BoosterConfig,
    eventEnvelopes: Array<EventEnvelope>,
    eventStore: EventStore
  ): Promise<Array<EventEnvelope>> {
    const logger = getLogger(config, 'BoosterEventDispatcher#filterDispatched')
    const filteredResults = await Promise.all(
      eventEnvelopes.map(async (eventEnvelope) => {
        const result = await eventStore.storeDispatchedEvent(eventEnvelope)
        if (!result) {
          logger.warn('Event has already been dispatched. Skipping.', eventEnvelope)
        }
        return result
      })
    )

    return eventEnvelopes.filter((_, index) => filteredResults[index])
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

  @Trace(TraceActionTypes.EVENT_HANDLERS_PROCESS)
  private static async dispatchEntityEventsToEventHandlers(
    entityEventEnvelopes: Array<EventEnvelope | NotificationInterface>,
    config: BoosterConfig
  ): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher.dispatchEntityEventsToEventHandlers')
    for (const eventEnvelope of entityEventEnvelopes) {
      let eventHandlers = config.eventHandlers[eventEnvelope.typeName] || []
      const globalEventHandler = config.eventHandlers[BOOSTER_GLOBAL_EVENT_HANDLERS]
      if (globalEventHandler && globalEventHandler.length > 0) {
        eventHandlers = eventHandlers.concat(globalEventHandler)
      }
      if (!eventHandlers || eventHandlers.length == 0) {
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
      const logger = getLogger(config, 'BoosterEventProcessor#handleEvent')
      logger.debug('Calling "handle" method on event handler: ', eventHandler)
      await eventHandler.handle(eventInstance, register)
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
      const error = await globalErrorDispatcher.dispatch(
        new EventHandlerGlobalError(eventEnvelope, eventInstance, e.eventHandlerMetadata, e)
      )
      if (error) throw error
    }
    await RegisterHandler.handle(config, register)
  }
}
