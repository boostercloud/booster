import {
  BoosterConfig,
  ScheduledCommandEnvelope,
  Logger,
  Register,
  NotFoundError,
  ScheduledCommandInterface,
  ScheduleCommandGlobalError,
} from '@boostercloud/framework-types'
import { RegisterHandler } from './booster-register-handler'
import { BoosterGlobalErrorDispatcher } from './booster-global-error-dispatcher'

export class BoosterScheduledCommandDispatcher {
  private readonly globalErrorDispatcher: BoosterGlobalErrorDispatcher

  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {
    this.globalErrorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
  }

  public async dispatchCommand(commandEnvelope: ScheduledCommandEnvelope): Promise<void> {
    this.logger.debug('Dispatching the following scheduled command envelope: ', commandEnvelope)

    const commandMetadata = this.config.scheduledCommandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${commandEnvelope.typeName}`)
    }

    const commandClass = commandMetadata.class
    this.logger.debug('Found the following command:', commandClass.name)
    const command = commandClass as ScheduledCommandInterface
    const register = new Register(commandEnvelope.requestID, undefined, commandEnvelope.context)
    try {
      this.logger.debug('Calling "handle" method on command: ', command)
      await command.handle(register)
    } catch (e) {
      throw await this.globalErrorDispatcher.dispatch(new ScheduleCommandGlobalError(e))
    }
    this.logger.debug('Command dispatched with register: ', register)
    await RegisterHandler.handle(this.config, this.logger, register)
  }
  /**
   * Entry point to dispatch events coming from the cloud provider.
   * @param request request from the cloud provider
   * @param logger
   */
  public async dispatch(request: unknown): Promise<void> {
    const envelopeOrError = await this.config.provider.scheduled.rawToEnvelope(request, this.logger)
    this.logger.debug('Received ScheduledCommand envelope...', envelopeOrError)
    await this.dispatchCommand(envelopeOrError)
  }
}
