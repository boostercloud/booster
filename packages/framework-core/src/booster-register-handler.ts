import {
  BoosterConfig,
  Register,
  Instance,
  EventInterface,
  EventEnvelope,
  NotFoundError,
  CommandInterface,
  CommandEnvelope,
  Logger,
} from '@boostercloud/framework-types'

export class RegisterHandler {
  protected constructor(readonly register: Register, readonly config: BoosterConfig, readonly logger: Logger) {}

  public static async handle(register: Register, config: BoosterConfig, logger: Logger): Promise<void> {
    const registerHandler = new RegisterHandler(register, config, logger)
    await Promise.all([
      // Run event publications and command submissions in parallel
      registerHandler.publishEvents(),
      registerHandler.submitCommands(),
    ])
  }

  private async publishEvents(): Promise<void> {
    return this.config.provider.publishEvents(
      this.register.eventList.map(this.wrapEvent.bind(this)),
      this.config,
      this.logger
    )
  }

  private async submitCommands(): Promise<void> {
    return this.config.provider.submitCommands(
      this.register.commandList.map(this.wrapCommand.bind(this)),
      this.config,
      this.logger
    )
  }

  private wrapEvent(event: Instance & EventInterface): EventEnvelope {
    const eventTypeName = event.constructor.name
    const reducerInfo = this.config.reducers[eventTypeName]
    if (!reducerInfo) {
      throw new NotFoundError(
        `Couldn't find information about event ${eventTypeName}. Is the event handled by any entity?`
      )
    }

    return {
      version: this.config.currentVersionFor(eventTypeName),
      kind: 'event',
      entityID: event.entityID(),
      requestID: this.register.requestID,
      currentUser: this.register.currentUser,
      entityTypeName: reducerInfo.class.name,
      typeName: eventTypeName,
      value: event,
      createdAt: new Date().toISOString(),
    }
  }

  private wrapCommand(command: Instance & CommandInterface): CommandEnvelope {
    const commandTypeName = command.constructor.name

    return {
      currentUser: this.register.currentUser,
      requestID: this.register.requestID,
      typeName: commandTypeName,
      version: this.config.currentVersionFor(commandTypeName),
      value: command,
    }
  }
}
