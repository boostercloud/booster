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
    return config.provider.publishEvents(
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
        `Couldn't find information about event ${eventTypeName}. Is the event handled by any entity?`
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
