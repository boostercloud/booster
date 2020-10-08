import {
  BoosterConfig,
  ScheduledCommandEnvelope,
  Logger,
  Register,
  NotFoundError,
  ScheduledCommandInterface,
} from '@boostercloud/framework-types'
import { RegisterHandler } from './booster-register-handler'

export class BoosterScheduledCommandDispatcher {
  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {}

  public async dispatchCommand(commandEnvelope: ScheduledCommandEnvelope): Promise<void> {
    this.logger.debug('Dispatching the following scheduled command envelope: ', commandEnvelope)

    const commandMetadata = this.config.scheduledCommandHandlers[commandEnvelope.typeName]
    if (!commandMetadata) {
      throw new NotFoundError(`Could not find a proper handler for ${commandEnvelope.typeName}`)
    }

    const commandClass = commandMetadata.class
    this.logger.debug('Found the following command:', commandClass.name)
    const command = commandClass as ScheduledCommandInterface
    const register = new Register(commandEnvelope.requestID)
    this.logger.debug('Calling "handle" method on command: ', command)
    await command.handle(register)
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
