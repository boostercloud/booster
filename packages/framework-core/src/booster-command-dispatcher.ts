import {
  BoosterConfig,
  CommandEnvelope,
  Register,
  InvalidParameterError,
  NotAuthorizedError,
  NotFoundError,
  CommandHandlerGlobalError,
} from '@boostercloud/framework-types'
import { BoosterAuth } from './booster-auth'
import { RegisterHandler } from './booster-register-handler'
import { createInstance, getLogger } from '@boostercloud/framework-common-helpers'
import { applyBeforeFunctions } from './services/filter-helpers'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { Migrator } from './migrator'

export class BoosterCommandDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher

  public constructor(readonly config: BoosterConfig) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
  }

  public async dispatchCommand(commandEnvelope: CommandEnvelope): Promise<unknown> {
    const logger = getLogger(this.config, 'BoosterCommandDispatcher#dispatchCommand')
    logger.debug('Dispatching the following command envelope: ', commandEnvelope)
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
    logger.debug('Found the following command:', commandClass.name)

    const migratedCommandEnvelope = await new Migrator(this.config).migrate<CommandEnvelope>(commandEnvelope)
    let result: unknown
    const register: Register = new Register(
      migratedCommandEnvelope.requestID,
      migratedCommandEnvelope.responseHeaders,
      migratedCommandEnvelope.currentUser,
      migratedCommandEnvelope.context
    )
    try {
      const commandInput = await applyBeforeFunctions(
        migratedCommandEnvelope.value,
        commandMetadata.before,
        migratedCommandEnvelope.currentUser
      )

      const commandInstance = createInstance(commandClass, commandInput)

      logger.debug('Calling "handle" method on command: ', commandClass)
      result = await commandClass.handle(commandInstance, register)
    } catch (e) {
      const error = await this.globalErrorDispatcher.dispatch(new CommandHandlerGlobalError(migratedCommandEnvelope, e))
      if (error) throw error
    }
    logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, register)
    return result
  }
}
