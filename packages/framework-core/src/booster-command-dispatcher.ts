import {
  BoosterConfig,
  CommandEnvelope,
  Logger,
  Register,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { RegisterHandler } from './booster-register-handler'

export class BoosterCommandDispatcher {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

  /**
   * Dispatches command messages to your application.
   * @deprecated This the entry point used when requests come directly trough HTTP API, use GraphQl instead
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public async dispatch(rawCommand: any): Promise<any> {
    this.logger.debug('Arrived raw command: ', rawCommand)
    try {
      const envelope = await this.config.provider.rawCommandToEnvelope(rawCommand)
      await this.dispatchCommand(envelope)
      return this.config.provider.requestSucceeded()
    } catch (error) {
      this.logger.error('When dispatching command: ', error)
      return await this.config.provider.requestFailed(error)
    }
  }

  public async dispatchCommand(commandEnvelope: CommandEnvelope): Promise<void> {
    this.logger.debug('Dispatching the following command envelope: ', commandEnvelope)
    if (!commandEnvelope.version) {
      throw new InvalidParameterError('The required command "version" was not present')
    }

    const commandMetadata = this.config.commandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${commandEnvelope.typeName}`)
    }

    if (!BoosterAuth.isUserAuthorized(commandMetadata.authorizedRoles, commandEnvelope.currentUser)) {
      throw new NotAuthorizedError(`Access denied for command '${commandEnvelope.typeName}'`)
    }

    const commandClass = commandMetadata.class
    this.logger.debug('Found the following command:', commandClass.name)
    const command = new commandClass()
    Object.assign(command, commandEnvelope.value)
    // TODO: Here we could call "command.validate()" so that the user can prevalidate
    // the command inputted by the user.
    const register = new Register(commandEnvelope.requestID, commandEnvelope.currentUser)
    this.logger.debug('Calling "handle" method on command: ', command)
    command.handle(register)
    this.logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, this.logger, register)
  }
}
