import {
  BoosterConfig,
  CommandEnvelope,
  Register,
  InvalidParameterError,
  NotFoundError,
  CommandHandlerGlobalError,
  TraceActionTypes,
  CommandInput,
} from '@boostercloud/framework-types'
import { RegisterHandler } from './booster-register-handler'
import { createInstance, getLogger } from '@boostercloud/framework-common-helpers'
import { applyBeforeFunctions } from './services/filter-helpers'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { SchemaMigrator } from './schema-migrator'
import { GraphQLResolverContext } from './services/graphql/common'
import { Trace } from './instrumentation'

export class BoosterCommandDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher

  public constructor(readonly config: BoosterConfig) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
  }

  @Trace(TraceActionTypes.COMMAND_HANDLER)
  public async dispatchCommand(commandEnvelope: CommandEnvelope, context: GraphQLResolverContext): Promise<unknown> {
    const logger = getLogger(this.config, 'BoosterCommandDispatcher#dispatchCommand')
    logger.debug('Dispatching the following command envelope: ', commandEnvelope)
    if (!commandEnvelope.version) {
      throw new InvalidParameterError('The required command "version" was not present')
    }

    const commandMetadata = this.config.commandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${commandEnvelope.typeName}`)
    }

    await commandMetadata.authorizer(commandEnvelope.currentUser, commandEnvelope)

    const commandClass = commandMetadata.class
    logger.debug('Found the following command:', commandClass.name)

    const migratedCommandEnvelope = await new SchemaMigrator(this.config).migrate<CommandEnvelope>(commandEnvelope)
    let result: unknown
    const register: Register = new Register(
      migratedCommandEnvelope.requestID,
      context.responseHeaders,
      RegisterHandler.flush,
      migratedCommandEnvelope.currentUser,
      migratedCommandEnvelope.context
    )
    try {
      const commandInput: CommandInput = await applyBeforeFunctions(
        migratedCommandEnvelope.value,
        commandMetadata.before,
        migratedCommandEnvelope.currentUser
      )

      const commandInstance = createInstance(commandClass, commandInput)

      logger.debug('Calling "handle" method on command: ', commandClass)
      result = await commandClass.handle(commandInstance, register)
    } catch (err) {
      const e = err as Error
      const error = await this.globalErrorDispatcher.dispatch(new CommandHandlerGlobalError(migratedCommandEnvelope, e))
      if (error) throw error
    }
    logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, register)
    return result
  }
}
