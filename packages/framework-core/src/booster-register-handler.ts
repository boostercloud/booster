import { BoosterConfig, Register, Instance, EventInterface, EventEnvelope, NotFoundError, CommandInterface, CommandEnvelope, Logger } from "@boostercloud/framework-types"

export class RegisterHandler {
  protected constructor(readonly register: Register, readonly config: BoosterConfig, readonly logger: Logger) {}

  public static async handle(register: Register, config: BoosterConfig, logger: Logger): Promise<void> {
    const registerHandler = new RegisterHandler(register, config, logger)
    await Promise.all([// Run event publications and command submissions in parallel
      registerHandler.publishEvents(),
      registerHandler.submitCommands()
    ])
  }

  private async publishEvents(): Promise<void> {
    await Promise.all( // Publish all events in parallel
      this.register.eventList.map((event: Instance & EventInterface): Promise<void> => {
        const eventEnvelope = this.wrapEvent(event)
        return this.config.provider.publishEvent(this.config, eventEnvelope, this.logger)
      })
    )
  }

  private async submitCommands(): Promise<void> {
    await Promise.all( // Submit all events in parallel
      this.register.commandList.map((command: Instance & CommandInterface): Promise<void> => {
        const commandEnvelope = this.wrapCommand(command)
        return this.config.provider.submitCommand(this.config, commandEnvelope, this.logger)
      })
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
      value: command
    }
  }
}