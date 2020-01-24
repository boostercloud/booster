import {
  BoosterConfig,
  CommandEnvelope,
  EventEnvelope,
  EventInterface,
  Instance,
  Logger,
  ProviderCommandsLibrary,
  Register,
} from '@boostercloud/framework-types'
import { Providers } from './providers'
import { BoosterAuth } from './booster-auth'

export class BoosterCommandDispatcher {
  /**
   * Dispatches command messages to your application.
   */
  public static async dispatch(
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    rawCommand: any, // We type this as `any` because we don't know yet which type the provider we will use or return
    config: BoosterConfig,
    logger: Logger
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  ): Promise<any> {
    logger.debug('Arrived raw command: ', rawCommand)
    const provider: ProviderCommandsLibrary = Providers.getLibrary(config)
    try {
      const envelope = await provider.rawCommandToEnvelope(rawCommand)
      const register = this.dispatchCommand(envelope, config, logger)
      logger.debug('Command dispatched with register: ', register)
      const resultEventEnvelopes = this.eventEnvelopesFromRegister(register, config)
      return provider.handleCommandResult(config, resultEventEnvelopes, logger)
    } catch (e) {
      logger.error('When dispatching command: ', e)
      return await provider.handleCommandError(config, e, logger)
    }
  }

  private static dispatchCommand(commandEnvelope: CommandEnvelope, config: BoosterConfig, logger: Logger): Register {
    logger.debug('Dispatching the following command envelope: ', commandEnvelope)
    if (!commandEnvelope.version) {
      throw new Error('The required command "version" was not present')
    }

    const commandMetadata = config.commandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new Error(`Couldn't find a proper handler for ${commandEnvelope.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(commandMetadata.authorizedRoles, commandEnvelope.currentUser)) {
      throw new Error(`Access denied for command '${commandEnvelope.typeName}'`)
    }

    const commandClass = commandMetadata.class
    logger.debug('Found the following command:', commandClass.name)
    const command = new commandClass()
    Object.assign(command, commandEnvelope.value)
    // TODO: Here we could call "command.validate()" so that the user can prevalidate
    // the command inputted by the user.
    const register = new Register(commandEnvelope.requestID, commandEnvelope.currentUser)
    logger.debug('Calling "handle" method on command: ', command)
    command.handle(register)
    return register
  }

  private static eventEnvelopesFromRegister(register: Register, config: BoosterConfig): Array<EventEnvelope> {
    return register.eventList.map((event): EventEnvelope => this.eventToEnvelope(register, event, config))
  }

  private static eventToEnvelope(
    register: Register,
    event: Instance & EventInterface,
    config: BoosterConfig
  ): EventEnvelope {
    const eventTypeName = event.constructor.name
    const reducerInfo = config.reducers[eventTypeName]
    if (!reducerInfo) {
      throw new Error(`Couldn't find information about event ${eventTypeName}. Is the event handled by any entity?`)
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
