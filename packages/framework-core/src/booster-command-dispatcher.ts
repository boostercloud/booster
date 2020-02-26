import {
  BoosterConfig,
  CommandEnvelope,
  Logger,
  Register,
  ProviderLibrary,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { RegisterHandler } from './booster-register-handler'

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
    const provider: ProviderLibrary = config.provider
    try {
      const envelope = await provider.rawCommandToEnvelope(rawCommand)
      const register = this.dispatchCommand(envelope, config, logger)
      logger.debug('Command dispatched with register: ', register)
      await RegisterHandler.handle(register, config, logger)
      return provider.requestSucceeded()
    } catch (error) {
      logger.error('When dispatching command: ', error)
      return await provider.requestFailed(error)
    }
  }

  private static dispatchCommand(commandEnvelope: CommandEnvelope, config: BoosterConfig, logger: Logger): Register {
    logger.debug('Dispatching the following command envelope: ', commandEnvelope)
    if (!commandEnvelope.version) {
      throw new InvalidParameterError('The required command "version" was not present')
    }

    const commandMetadata = config.commandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${commandEnvelope.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(commandMetadata.authorizedRoles, commandEnvelope.currentUser)) {
      throw new NotAuthorizedError(`Access denied for command '${commandEnvelope.typeName}'`)
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
}
