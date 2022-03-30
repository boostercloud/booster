import {
  BoosterConfig,
  CommandEnvelope,
  Logger,
  Register,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  CommandHandlerGlobalError,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { RegisterHandler } from './booster-register-handler'
import { createInstance } from '@boostercloud/framework-common-helpers'
import { applyBeforeFunctions } from './services/filter-helpers'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'

export class BoosterCommandDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
  }

  public async dispatchCommand(commandEnvelope: CommandEnvelope): Promise<unknown> {
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

    let result: unknown
    let register: Register
    try {
      const commandInput = await applyBeforeFunctions(
        commandEnvelope.value,
        commandMetadata.before,
        commandEnvelope.currentUser
      )

      const commandInstance = createInstance(commandClass, commandInput)

      register = new Register(commandEnvelope.requestID, commandEnvelope.currentUser, commandEnvelope.context)
      this.logger.debug('Calling "handle" method on command: ', commandClass)
      result = await commandClass.handle(commandInstance, register)
    } catch (e) {
      throw await this.globalErrorDispatcher.dispatch(new CommandHandlerGlobalError(commandEnvelope, e))
    }
    this.logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, this.logger, register)
    return result
  }
}
