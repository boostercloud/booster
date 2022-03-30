import { expect } from './expect'
import { BoosterGlobalErrorDispatcher } from '../dist/booster-global-error-dispatcher'
import {
  BoosterConfig,
  CommandEnvelope,
  CommandHandlerGlobalError,
  EntityInterface,
  EventHandlerGlobalError,
  EventInterface,
  GlobalErrorContainer,
  GlobalErrorHandlerMetadata,
  Logger,
  ProjectionGlobalError,
  ReadModelInterface,
  ReducerGlobalError,
  ScheduleCommandGlobalError,
} from '@boostercloud/framework-types'
import { GlobalErrorHandler } from '../src'
import { restore } from 'sinon'
import { Booster } from '../src/booster'

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('BoosterGlobalErrorDispatcher', () => {
  let config: BoosterConfig
  const baseError = new Error('test')

  beforeEach(() => {
    config = new BoosterConfig('test')
  })

  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      config.globalErrorsHandler = undefined
    })
    config.globalErrorsHandler = undefined
    restore()
  })

  it('should dispatch original error if none is defined as a globalErrorsHandler', async () => {
    const globalError = new GlobalErrorContainer(baseError)
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(globalError)).to.be.rejectedWith(baseError)
  })

  it('should dispatch handleGenericError if globalErrorsHandler is defined for a GlobalErrorContainer', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(error: Error): Promise<Error> {
        return new Error(`${error}.onScheduledCommandHandlerError`)
      }

      public static async onError?(error: Error): Promise<Error> {
        return new Error(`${error}.updated error`)
      }
    }

    const globalError = new GlobalErrorContainer(baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(globalError)).to.be.rejectedWith(`${baseError.message}.updated error`)
  })

  it('should dispatch original error if there is an error processing them', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(error: Error): Promise<Error> {
        throw new Error('failed')
      }

      public static async onError(error: Error): Promise<Error> {
        return new Error(`${error}.onError`)
      }
    }

    const scheduleCommandGlobalError = new ScheduleCommandGlobalError(baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(scheduleCommandGlobalError)).to.be.rejectedWith('failed')
  })

  it('should dispatch specific and generic handler if both are defined for a specific error', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(error: Error): Promise<Error> {
        return new Error(`${error}.onScheduledCommandHandlerError`)
      }

      public static async onError(error: Error): Promise<Error> {
        return new Error(`${error}.onError`)
      }
    }

    const scheduleCommandGlobalError = new ScheduleCommandGlobalError(baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(scheduleCommandGlobalError)).to.be.rejectedWith(
      `${baseError.message}.onScheduledCommandHandlerError.onError`
    )
  })

  it('should dispatch CommandHandlerGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onCommandHandlerError(error: Error): Promise<Error> {
        return new Error(`${error}.onCommandHandlerError`)
      }
    }
    const mockCommand = {} as CommandEnvelope
    const commandHandlerGlobalError = new CommandHandlerGlobalError(mockCommand, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(commandHandlerGlobalError)).to.be.rejectedWith(
      `${baseError.message}.onCommandHandlerError`
    )
  })

  it('should dispatch EventHandlerGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onDispatchEventHandlerError(error: Error, eventInstance: EventInterface): Promise<Error> {
        return new Error(`${error}.onDispatchEventHandlerError`)
      }
    }
    const mockEventInstance = {} as EventInterface
    const eventHandlerGlobalError = new EventHandlerGlobalError(mockEventInstance, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(eventHandlerGlobalError)).to.be.rejectedWith(
      `${baseError.message}.onDispatchEventHandlerError`
    )
  })

  it('should dispatch ReducerGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onReducerError(
        error: Error,
        eventInstance: EventInterface,
        snapshotInstance: EntityInterface | null
      ): Promise<Error> {
        return new Error(`${error}.onReducerError`)
      }
    }
    const mockEventInstance = {} as EventInterface
    const mockSnapshotInstance = {} as EntityInterface
    const reducerGlobalError = new ReducerGlobalError(mockEventInstance, mockSnapshotInstance, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(reducerGlobalError)).to.be.rejectedWith(`${baseError.message}.onReducerError`)
  })

  it('should dispatch ProjectionGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onProjectionError(
        error: Error,
        entity: EntityInterface,
        readModel: ReadModelInterface | undefined
      ): Promise<Error> {
        return new Error(`${error}.onProjectionError`)
      }
    }
    const mockEntity = {} as EntityInterface
    const mockReadModel = {} as ReadModelInterface
    const projectionGlobalError = new ProjectionGlobalError(mockEntity, mockReadModel, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config, logger)
    await expect(errorDispatcher.dispatch(projectionGlobalError)).to.be.rejectedWith(
      `${baseError.message}.onProjectionError`
    )
  })
})
