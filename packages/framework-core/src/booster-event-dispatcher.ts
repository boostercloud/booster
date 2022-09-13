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

export class BoosterEventDispatcher {
  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param rawEvents List of RAW events from the cloud provider
   * @param config
   * @param logger
   */
  public static async dispatch(rawEvents: unknown, config: BoosterConfig): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher#dispatch')
    const eventStore = new EventStore(config)
    const readModelStore = new ReadModelStore(config)

    logger.debug(
      'Event workflow started for raw events:',
      // TODO: Make this compatible with ES Modules
      // More info: https://github.com/sindresorhus/eslint-plugin-unicorn/blob/v43.0.2/docs/rules/prefer-module.md
      // eslint-disable-next-line unicorn/prefer-module
      require('node:util').inspect(rawEvents, false, undefined, false)
    )
    try {
      await RawEventsParser.streamPerEntityEvents(
        config,
        rawEvents,
        BoosterEventDispatcher.eventProcessor(eventStore, readModelStore)
      )
    } catch (error) {
      logger.error('Unhandled error while dispatching event: ', error)
    }
  }

  private static eventProcessor(eventStore: EventStore, readModelStore: ReadModelStore): EventsStreamingCallback {
    return async (entityName, entityID, eventEnvelopes, config) => {
      // TODO: Separate into two independent processes the snapshotting/read-model generation process from the event handling process`
      await BoosterEventDispatcher.snapshotAndUpdateReadModels(
        config,
        entityName,
        entityID,
        eventEnvelopes,
        eventStore,
        readModelStore
      )
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
    const entitySnapshot = await eventStore.calculateAndStoreEntitySnapshot(entityName, entityID, envelopes)
    if (!entitySnapshot) {
      logger.debug('No new snapshot generated, skipping read models projection')
      return
    }

    logger.debug('Snapshot loaded and started read models projection:', entitySnapshot)
    await readModelStore.project(entitySnapshot)
  }

  private static async dispatchEntityEventsToEventHandlers(
    entityEventEnvelopes: Array<EventEnvelope>,
    config: BoosterConfig
  ): Promise<void> {
    const logger = getLogger(config, 'BoosterEventDispatcher.dispatchEntityEventsToEventHandlers')
    for (const eventEnvelope of entityEventEnvelopes) {
      const eventHandlers = config.eventHandlers[eventEnvelope.typeName]
      if (!eventHandlers || eventHandlers.length === 0) {
        logger.debug(`No event-handlers found for event ${eventEnvelope.typeName}. Skipping...`)
        continue
      }
      const eventClass = config.events[eventEnvelope.typeName]
      await Promises.allSettledAndFulfilled(
        eventHandlers.map(async (eventHandler: EventHandlerInterface) => {
          const eventInstance = createInstance(eventClass.class, eventEnvelope.value)
          const register = new Register(eventEnvelope.requestID, {}, eventEnvelope.currentUser)
          logger.debug('Calling "handle" method on event handler: ', eventHandler)
          try {
            await eventHandler.handle(eventInstance, register)
          } catch (error_) {
            const globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
            const error = await globalErrorDispatcher.dispatch(new EventHandlerGlobalError(eventInstance, error_))
            if (error) throw error
          }
          return RegisterHandler.handle(config, register)
        })
      )
    }
  }
}
