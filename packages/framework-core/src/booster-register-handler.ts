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
  UUID,
} from '@boostercloud/framework-types'

export class RegisterHandler {
  protected constructor(readonly register: Register, readonly config: BoosterConfig, readonly logger: Logger) {}

  public static async handle(config: BoosterConfig, logger: Logger, register: Register): Promise<void> {
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
    /* TODO: As the current AWS implementation for commands is synchronous, we need to handle
     * the commands here and then their responses recursively.
     * Fixing this requires a significant change in the architecture to make Commands asynchronous.
     */
    await Promise.all(
      this.register.commandList.map(
        (command): Promise<void> => {
          // TODO: Building a wrapper here just to make compiler stop complaining until we can refactor this code.
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          const commandEnvelope = this.wrapCommand(command)
          // TODO: We assign a new UUID here as the request ID, as it's a brand new command. We might want to keep a trace connecting this command with the one that generated it though.
          const register = new Register(UUID.generate(), this.register.currentUser)
          commandEnvelope.value.handle(register)
          return RegisterHandler.handle(this.config, this.logger, register)
        }
      )
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
