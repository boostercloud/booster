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
import { createInstance } from '@boostercloud/framework-common-helpers'
import { applyAfterFunctions, applyBeforeFunctions, applyOnErrorFunctions } from './services/filter-helpers'

export class BoosterCommandDispatcher {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

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

    const register = new Register(commandEnvelope.requestID, commandEnvelope.currentUser, commandEnvelope.context)
    const commandInput = await applyBeforeFunctions(commandEnvelope.value, commandMetadata.before, register)

    const commandInstance = createInstance(commandClass, commandInput)

    try {
      this.logger.debug('Calling "handle" method on command: ', commandClass)
      const result = await commandClass.handle(commandInstance, register)

      this.logger.debug('Calling "after" methods on command: ', commandClass)
      await applyAfterFunctions(result, commandEnvelope.value, commandMetadata.after, register)

      this.logger.debug('Command dispatched with register: ', register)
      await RegisterHandler.handle(this.config, this.logger, register)
      return result
    } catch (e) {
      throw await applyOnErrorFunctions(e, commandEnvelope.value, commandMetadata.onError, register)
    }
  }
}
