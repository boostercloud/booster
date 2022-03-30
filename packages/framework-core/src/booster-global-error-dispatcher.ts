import {
  BoosterConfig,
  GlobalErrorHandlerInterface,
  Logger,
  GlobalErrorContainer,
  CommandHandlerGlobalError,
  EventHandlerGlobalError,
  ScheduleCommandGlobalError,
  ReducerGlobalError,
  ProjectionGlobalError,
} from '@boostercloud/framework-types'

export class BoosterGlobalErrorDispatcher {
  public readonly errorHandler: GlobalErrorHandlerInterface | undefined

  public constructor(readonly config: BoosterConfig, readonly logger: Logger) {
    this.errorHandler = this.config.globalErrorsHandler?.class
  }

  public async dispatch(error: GlobalErrorContainer): Promise<void> {
    if (!this.errorHandler) throw error.originalError

    let newError: Error = error.originalError

    try {
      switch (error.constructor) {
        case CommandHandlerGlobalError:
          newError = await this.handleCommandError(error)
          break
        case ScheduleCommandGlobalError:
          newError = await this.handleScheduleError(error)
          break
        case EventHandlerGlobalError:
          newError = await this.handleEventHandlerError(error)
          break
        case ReducerGlobalError:
          newError = await this.handleReducerError(error)
          break
        case ProjectionGlobalError:
          newError = await this.handleProjectionError(error)
          break
      }

      newError = await this.handleGenericError(newError)
    } catch (e) {
      this.logger.error(
        `Unhandled error inside the global error handler. When handling error ${error.originalError}, another error occurred`,
        e
      )
      throw e
    }
    throw newError
  }

  private async handleCommandError(error: GlobalErrorContainer): Promise<Error> {
    if (!this.errorHandler || !this.errorHandler.onCommandHandlerError) throw error.originalError
    const currentError = error as CommandHandlerGlobalError
    return await this.errorHandler.onCommandHandlerError(currentError.originalError, currentError.command)
  }

  private async handleScheduleError(error: GlobalErrorContainer): Promise<Error> {
    if (!this.errorHandler || !this.errorHandler.onScheduledCommandHandlerError) throw error.originalError
    const currentError = error as ScheduleCommandGlobalError
    return await this.errorHandler.onScheduledCommandHandlerError(currentError.originalError)
  }

  private async handleEventHandlerError(error: GlobalErrorContainer): Promise<Error> {
    if (!this.errorHandler || !this.errorHandler.onDispatchEventHandlerError) throw error.originalError
    const currentError = error as EventHandlerGlobalError
    return await this.errorHandler.onDispatchEventHandlerError(currentError.originalError, currentError.eventInstance)
  }

  private async handleReducerError(error: GlobalErrorContainer): Promise<Error> {
    if (!this.errorHandler || !this.errorHandler.onReducerError) throw error.originalError
    const currentError = error as ReducerGlobalError
    return await this.errorHandler.onReducerError(
      currentError.originalError,
      currentError.eventInstance,
      currentError.snapshotInstance
    )
  }

  private async handleProjectionError(error: GlobalErrorContainer): Promise<Error> {
    if (!this.errorHandler || !this.errorHandler.onProjectionError) throw error.originalError
    const currentError = error as ProjectionGlobalError
    return await this.errorHandler.onProjectionError(
      currentError.originalError,
      currentError.entity,
      currentError.readModel
    )
  }

  private async handleGenericError(error: Error): Promise<Error> {
    if (!this.errorHandler || !this.errorHandler.onError) throw error
    return await this.errorHandler.onError(error)
  }
}
