import { expect } from './expect'
import {
  BoosterConfig,
  CommandEnvelope,
  CommandHandlerGlobalError,
  CommandMetadata,
  EntityInterface,
  EntitySnapshotEnvelope,
  EventEnvelope,
  EventHandlerGlobalError,
  EventInterface,
  GlobalErrorContainer,
  GlobalErrorHandlerMetadata,
  ProjectionGlobalError,
  ProjectionMetadata,
  ReadModelInterface,
  ReducerGlobalError,
  ReducerMetadata,
  ScheduleCommandGlobalError,
  ScheduledCommandEnvelope,
  ScheduledCommandMetadata,
} from '@boostercloud/framework-types'
import { GlobalErrorHandler } from '../src'
import { restore } from 'sinon'
import { Booster } from '../src/booster'
import { BoosterGlobalErrorDispatcher } from '../src/booster-global-error-dispatcher'
import 'mocha'

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
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    await expect(errorDispatcher.dispatch(globalError)).to.eventually.eq(baseError)
  })

  it('should dispatch handleGenericError if globalErrorsHandler is defined for a GlobalErrorContainer', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {
        return new Error(`${error}.onScheduledCommandHandlerError`)
      }

      public static async onError?(error: Error | undefined): Promise<Error | undefined> {
        return new Error(`${error}.updated error`)
      }
    }

    const globalError = new GlobalErrorContainer(baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(globalError)
    expect(result).to.be.instanceof(Error)
    expect(result?.toString()).to.be.eq(`Error: Error: ${baseError.message}.updated error`)
  })

  it('should dispatch original error if there is an error processing them', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(
        error: Error,
        scheduledCommandEnvelope: ScheduledCommandEnvelope,
        scheduledCommandMetadata: ScheduledCommandMetadata
      ): Promise<Error | undefined> {
        throw new Error('failed')
      }

      public static async onError(error: Error | undefined): Promise<Error | undefined> {
        return new Error(`${error}.onError`)
      }
    }

    const envelope: ScheduledCommandEnvelope = {} as ScheduledCommandEnvelope
    const metadata: ScheduledCommandMetadata = {} as ScheduledCommandMetadata
    const scheduleCommandGlobalError = new ScheduleCommandGlobalError(envelope, metadata, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(scheduleCommandGlobalError)
    expect(result?.toString()).to.be.eq('Error: failed')
  })

  it('should dispatch specific and generic handler if both are defined for a specific error', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(
        error: Error,
        scheduledCommandEnvelope: ScheduledCommandEnvelope,
        scheduledCommandMetadata: ScheduledCommandMetadata
      ): Promise<Error | undefined> {
        return new Error(`${error}.onScheduledCommandHandlerError`)
      }

      public static async onError(error: Error | undefined): Promise<Error | undefined> {
        return new Error(`${error}.onError`)
      }
    }

    const envelope: ScheduledCommandEnvelope = {} as ScheduledCommandEnvelope
    const metadata: ScheduledCommandMetadata = {} as ScheduledCommandMetadata
    const scheduleCommandGlobalError = new ScheduleCommandGlobalError(envelope, metadata, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(scheduleCommandGlobalError)
    expect(result?.toString()).to.be.eq(
      `Error: Error: Error: ${baseError.message}.onScheduledCommandHandlerError.onError`
    )
  })

  it('should dispatch CommandHandlerGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onCommandHandlerError(error: Error): Promise<Error | undefined> {
        return new Error(`${error}.onCommandHandlerError`)
      }
    }
    const mockCommand = {} as CommandEnvelope
    const mockMetadata: CommandMetadata = {} as CommandMetadata
    const commandHandlerGlobalError = new CommandHandlerGlobalError(mockCommand, mockMetadata, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(commandHandlerGlobalError)
    expect(result?.toString()).to.be.eq(`Error: Error: ${baseError.message}.onCommandHandlerError`)
  })

  it('should dispatch EventHandlerGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onDispatchEventHandlerError(
        error: Error,
        eventInstance: EventInterface
      ): Promise<Error | undefined> {
        return new Error(`${error}.onDispatchEventHandlerError`)
      }
    }
    const mockEventInstance = {} as EventInterface
    const mockEventEnvelope: EventEnvelope = {} as EventEnvelope
    const mockEventHandlerMetadata = {}
    const eventHandlerGlobalError = new EventHandlerGlobalError(
      mockEventEnvelope,
      mockEventInstance,
      mockEventHandlerMetadata,
      baseError
    )
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(eventHandlerGlobalError)
    expect(result?.toString()).to.be.eq(`Error: Error: ${baseError.message}.onDispatchEventHandlerError`)
  })

  it('should dispatch ReducerGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onReducerError(
        error: Error,
        eventEnvelope: EventEnvelope,
        reducerMetadata: ReducerMetadata,
        eventInstance: EventInterface,
        snapshotInstance: EntityInterface | null
      ): Promise<Error> {
        return new Error(`${error}.onReducerError`)
      }
    }
    const mockEventEnvelope: EventEnvelope = {} as EventEnvelope
    const mockEventInstance = {} as EventInterface
    const mockSnapshotInstance = {} as EntityInterface
    const reducerMetadata: ReducerMetadata = {} as ReducerMetadata
    const reducerGlobalError = new ReducerGlobalError(
      mockEventEnvelope,
      mockEventInstance,
      mockSnapshotInstance,
      reducerMetadata,
      baseError
    )
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(reducerGlobalError)
    expect(result?.toString()).to.be.eq(`Error: Error: ${baseError.message}.onReducerError`)
  })

  it('should dispatch ProjectionGlobalError', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onProjectionError(
        error: Error,
        entityEnvelope: EntitySnapshotEnvelope,
        projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
        entity: EntityInterface,
        readModel: ReadModelInterface | undefined
      ): Promise<Error | undefined> {
        return new Error(`${error}.onProjectionError`)
      }
    }
    const mockEntityEnvelope: EntitySnapshotEnvelope = {} as EntitySnapshotEnvelope
    const mockEntity = {} as EntityInterface
    const mockReadModel = {} as ReadModelInterface
    const mockProjectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface> = {} as ProjectionMetadata<
      EntityInterface,
      ReadModelInterface
    >
    const projectionGlobalError = new ProjectionGlobalError(
      mockEntityEnvelope,
      mockEntity,
      mockReadModel,
      mockProjectionMetadata,
      baseError
    )
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(projectionGlobalError)
    expect(result?.toString()).to.be.eq(`Error: Error: ${baseError.message}.onProjectionError`)
  })

  it('should ignore errors on ProjectionGlobalError if undefined is returned', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onProjectionError(
        error: Error,
        entityEnvelope: EntitySnapshotEnvelope,
        projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
        entity: EntityInterface,
        readModel: ReadModelInterface | undefined
      ): Promise<Error | undefined> {
        return undefined
      }
    }
    const mockEntityEnvelope: EntitySnapshotEnvelope = {} as EntitySnapshotEnvelope
    const mockEntity = {} as EntityInterface
    const mockReadModel = {} as ReadModelInterface
    const mockProjectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface> = {} as ProjectionMetadata<
      EntityInterface,
      ReadModelInterface
    >
    const projectionGlobalError = new ProjectionGlobalError(
      mockEntityEnvelope,
      mockEntity,
      mockReadModel,
      mockProjectionMetadata,
      baseError
    )
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(projectionGlobalError)
    expect(result).to.be.undefined
  })

  it('should ignore erros if generic handler returns an undefined', async () => {
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onScheduledCommandHandlerError(
        error: Error,
        scheduledCommandEnvelope: ScheduledCommandEnvelope,
        scheduledCommandMetadata: ScheduledCommandMetadata
      ): Promise<Error | undefined> {
        return new Error(`${error}.onScheduledCommandHandlerError`)
      }

      public static async onError(error: Error | undefined): Promise<Error | undefined> {
        return undefined
      }
    }

    const envelope: ScheduledCommandEnvelope = {} as ScheduledCommandEnvelope
    const metadata: ScheduledCommandMetadata = {} as ScheduledCommandMetadata
    const scheduleCommandGlobalError = new ScheduleCommandGlobalError(envelope, metadata, baseError)
    config.globalErrorsHandler = { class: ErrorHandler } as GlobalErrorHandlerMetadata
    const errorDispatcher = new BoosterGlobalErrorDispatcher(config)
    const result = await errorDispatcher.dispatch(scheduleCommandGlobalError)
    expect(result).to.be.undefined
  })
})
