import {
  BoosterConfig,
  ScheduledCommandEnvelope,
  Register,
  NotFoundError,
  ScheduledCommandInterface,
  ScheduleCommandGlobalError,
  TraceActionTypes,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { RegisterHandler } from './booster-register-handler'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'
import { Trace } from './instrumentation'

export class BoosterScheduledCommandDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher

  public constructor(readonly config: BoosterConfig) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config)
  }

  @Trace(TraceActionTypes.SCHEDULED_COMMAND_HANDLER)
  public async dispatchCommand(commandEnvelope: ScheduledCommandEnvelope): Promise<void> {
    const logger = getLogger(this.config, 'BoosterScheduledCommandDispatcher#dispatchCommand')
    logger.debug('Dispatching the following scheduled command envelope: ', commandEnvelope)

    const commandMetadata = this.config.scheduledCommandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${commandEnvelope.typeName}`)
    }

    const commandClass = commandMetadata.class
    logger.debug('Found the following command:', commandClass.name)
    const command = commandClass as ScheduledCommandInterface
    const register = new Register(
      commandEnvelope.requestID,
      {},
      RegisterHandler.flush,
      undefined,
      commandEnvelope.context
    )
    try {
      logger.debug('Calling "handle" method on command: ', command)
      await command.handle(register)
    } catch (e) {
      const error = await this.globalErrorDispatcher.dispatch(new ScheduleCommandGlobalError(e))
      if (error) throw error
    }
    logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, register)
  }

  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param request request from the cloud provider
   * @param logger
   */
  public async dispatch(request: unknown): Promise<void> {
    const logger = getLogger(this.config, 'BoosterScheduledCommandDispatcher#dispatch')
    const envelopeOrError = await this.config.provider.scheduled.rawToEnvelope(this.config, request)
    logger.debug('Received ScheduledCommand envelope...', envelopeOrError)
    await this.dispatchCommand(envelopeOrError)
  }
}
