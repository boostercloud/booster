import {
  BoosterConfig,
  CommandEnvelope,
  Logger,
  Register,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  CommandBeforeFunction,
  UserEnvelope,
  CommandInput,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { RegisterHandler } from './booster-register-handler'
import { createInstance } from '@boostercloud/framework-common-helpers'

export class BoosterCommandDispatcher {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

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

    const commandInput = this.applyBeforeFunctions(
      commandEnvelope.value,
      commandMetadata.before,
      commandEnvelope.currentUser
    )

    const commandInstance = createInstance(commandClass, commandInput)

    const register = new Register(commandEnvelope.requestID, commandEnvelope.currentUser)
    this.logger.debug('Calling "handle" method on command: ', commandClass)
    await commandClass.handle(commandInstance, register)
    this.logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, this.logger, register)
  }

  private applyBeforeFunctions(
    commandInput: CommandInput,
    beforeHooks: Array<CommandBeforeFunction>,
    currentUser?: UserEnvelope
  ): CommandInput {
    return beforeHooks.reduce((currentInput, before) => before(currentInput, currentUser), commandInput)
  }
}
