/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { GlobalErrorHandler, Booster } from '../../src'
import { CommandEnvelope, EntityInterface, EventInterface, ReadModelInterface } from '@boostercloud/framework-types'

describe('the `GlobalErrorHandler` decorator', () => {
  afterEach(() => {
    Booster.configure('test', (config) => {
      config.appName = ''
      config.globalErrorsHandler = undefined
    })
  })

  it('adds the error handler class as an error handler in the Booster configuration', () => {
    // Register command
    @GlobalErrorHandler()
    class ErrorHandler {}

    // Make Booster be of any type to access private members
    const booster = Booster as any

    expect(booster.config.globalErrorsHandler.class).to.be.eq(ErrorHandler)
  })

  it('adds the error handler class as an error handler in the Booster configuration with expected methods', () => {
    // Register command
    @GlobalErrorHandler()
    class ErrorHandler {
      public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {
        return new Error('')
      }

      public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {
        return new Error('')
      }

      public static async onDispatchEventHandlerError(
        error: Error,
        eventInstance: EventInterface
      ): Promise<Error | undefined> {
        return new Error('')
      }

      public static async onProjectionError(
        error: Error,
        entity: EntityInterface,
        readModel: ReadModelInterface | undefined
      ): Promise<Error> {
        return new Error('')
      }

      public static async onReducerError(
        error: Error,
        eventInstance: EventInterface,
        snapshotInstance: EntityInterface | null
      ): Promise<Error> {
        return new Error('')
      }
    }

    // Make Booster be of any type to access private members
    const booster = Booster as any

    expect(booster.config.globalErrorsHandler.class).to.be.eq(ErrorHandler)
  })
})
