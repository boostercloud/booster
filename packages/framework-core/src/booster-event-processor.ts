import {
  TraceActionTypes,
  BoosterConfig,
  EventEnvelope,
  EventHandlerGlobalError,
  EventHandlerInterface,
  EventInterface,
  Register,
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

export class BoosterEventProcessor {
  /**
   * Function that will be called once for each entity from the `RawEventsParser.streamPerEntityEvents` method
   * after the page of events is grouped by entity.
   */
  public static eventProcessor(eventStore: EventStore, readModelStore: ReadModelStore): EventsStreamingCallback {
    return async (entityName, entityID, eventEnvelopes, config) => {
      const eventEnvelopesProcessors = [
        BoosterEventProcessor.dispatchEntityEventsToEventHandlers(eventEnvelopes, config),
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
          await this.handleEvent(eventHandler, eventInstance, register, config)
          return RegisterHandler.handle(config, register)
        })
      )
    }
  }

  @Trace(TraceActionTypes.HANDLE_EVENT)
  private static async handleEvent(
    eventHandler: EventHandlerInterface,
    eventInstance: EventInterface,
    register: Register,
    config: BoosterConfig
  ) {
    try {
      const logger = getLogger(config, 'BoosterEventProcessor#handleEvent')
      logger.debug('Calling "handle" method on event handler: ', eventHandler)
      await eventHandler.handle(eventInstance, register)
    } catch (e) {
      const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
      const error = await globalErrorDispatcher.dispatch(new EventHandlerGlobalError(eventInstance, e))
      if (error) throw error
    }
  }
}
