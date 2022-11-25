import {
  BoosterConfig,
  GlobalErrorHandlerInterface,
  GlobalErrorContainer,
  CommandHandlerGlobalError,
  EventHandlerGlobalError,
  ScheduleCommandGlobalError,
  ReducerGlobalError,
  ProjectionGlobalError,
  SnapshotPersistHandlerGlobalError,
  QueryHandlerGlobalError,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export class BoosterGlobalErrorDispatcher {
  public readonly errorHandler: GlobalErrorHandlerInterface | undefined

  public constructor(readonly config: BoosterConfig) {
    this.errorHandler = this.config.globalErrorsHandler?.class
  }

  public async dispatch(error: GlobalErrorContainer): Promise<Error | undefined> {
    const logger = getLogger(this.config, 'BoosterGlobalErrorDispatcher#dispatch')
    if (!this.errorHandler) return error.originalError

    let newError: Error | undefined = error.originalError

    try {
      switch (error.constructor) {
        case CommandHandlerGlobalError:
          newError = await this.handleCommandError(error)
          break
        case QueryHandlerGlobalError:
          newError = await this.handleQueryError(error)
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
        case SnapshotPersistHandlerGlobalError:
          newError = await this.handleSnapshotPersistError(error)
          break
      }

      newError = await this.handleGenericError(newError)
    } catch (e) {
      logger.error(
        `Unhandled error inside the global error handler. When handling error ${error.originalError}, another error occurred`,
        e
      )
      return e
    }
    if (newError) return newError
    return undefined
  }

  private async handleCommandError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onCommandHandlerError) throw error.originalError
    const currentError = error as CommandHandlerGlobalError
    return await this.errorHandler.onCommandHandlerError(currentError.originalError, currentError.command)
  }

  private async handleQueryError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onQueryHandlerError) throw error.originalError
    const currentError = error as QueryHandlerGlobalError
    return await this.errorHandler.onQueryHandlerError(currentError.originalError, currentError.query)
  }

  private async handleScheduleError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onScheduledCommandHandlerError) throw error.originalError
    const currentError = error as ScheduleCommandGlobalError
    return await this.errorHandler.onScheduledCommandHandlerError(currentError.originalError)
  }

  private async handleEventHandlerError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onDispatchEventHandlerError) throw error.originalError
    const currentError = error as EventHandlerGlobalError
    return await this.errorHandler.onDispatchEventHandlerError(currentError.originalError, currentError.eventInstance)
  }

  private async handleReducerError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onReducerError) throw error.originalError
    const currentError = error as ReducerGlobalError
    return await this.errorHandler.onReducerError(
      currentError.originalError,
      currentError.eventInstance,
      currentError.snapshotInstance
    )
  }

  private async handleProjectionError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onProjectionError) throw error.originalError
    const currentError = error as ProjectionGlobalError
    return await this.errorHandler.onProjectionError(
      currentError.originalError,
      currentError.entity,
      currentError.readModel
    )
  }

  private async handleSnapshotPersistError(error: GlobalErrorContainer): Promise<Error | undefined> {
    if (!this.errorHandler || !this.errorHandler.onSnapshotPersistError) throw error.originalError
    const currentError = error as SnapshotPersistHandlerGlobalError
    return this.errorHandler.onSnapshotPersistError(currentError.originalError, currentError.snapshot)
  }

  private async handleGenericError(error: Error | undefined): Promise<Error | undefined> {
    if (!error) return undefined
    if (!this.errorHandler || !this.errorHandler.onError) throw error
    return await this.errorHandler.onError(error)
  }
}
