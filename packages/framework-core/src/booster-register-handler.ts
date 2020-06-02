import {
  BoosterConfig,
  Register,
  Instance,
  EventInterface,
  EventEnvelope,
  NotFoundError,
  Logger,
} from '@boostercloud/framework-types'

export class RegisterHandler {
  public static async handle(config: BoosterConfig, logger: Logger, register: Register): Promise<void> {
    if (register.eventList.length == 0) {
      return
    }
    return config.provider.events.store(
      register.eventList.map(RegisterHandler.wrapEvent.bind(null, register, config)),
      config,
      logger
    )
  }

  private static wrapEvent(register: Register, config: BoosterConfig, event: Instance & EventInterface): EventEnvelope {
    const eventTypeName = event.constructor.name
    const reducerInfo = config.reducers[eventTypeName]
    if (!reducerInfo) {
      throw new NotFoundError(
        `Couldn't find information about event ${eventTypeName}. Is the event handled by an entity?`
      )
    }
    if (!event.entityID || !event.entityID()) {
      throw new Error(
        `Event ${eventTypeName} has an empty 'entityID' or the required 'entityID' method was not implemented. Make sure to return a string-compatible value identifying the entity this event belongs to.`
      )
    }

    return {
      version: config.currentVersionFor(eventTypeName),
      kind: 'event',
      entityID: event.entityID(),
      requestID: register.requestID,
      currentUser: register.currentUser,
      entityTypeName: reducerInfo.class.name,
      typeName: eventTypeName,
      value: event,
      createdAt: new Date().toISOString(),
    }
  }
}
