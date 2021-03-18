import { ReaderTaskEither, rightIO, chain, ask, run, fromTaskEither } from 'fp-ts/lib/ReaderTaskEither'
import { pipe } from 'fp-ts/lib/pipeable'
import { fold } from 'fp-ts/lib/Either'
import { tryCatch } from 'fp-ts/lib/TaskEither'
import { constVoid } from 'fp-ts/lib/function'
import { oraLogger } from '../services/logger'
import Brand from './brand'

/**
 * A Script represents some steps in a booster command, it stores the initial context
 * implicitly, and makes it available for all of the steps that define this process.
 */
export class Script<TContext> {
  private constructor(
    readonly contextResolver: Promise<TContext>,
    readonly errorHandlers: Record<string, (_: Error) => string>,
    readonly action: ReaderTaskEither<TContext, Error, void>
  ) {}

  /**
   * Convenience function to print a welcome message and initialize the context of the script
   *
   * @param initialMessage The message to show in the console
   * @param contextResolver A promise that fulfills into a valid context object
   */
  public static init = <TContext>(initialMessage: string, contextResolver: Promise<TContext>): Script<TContext> =>
    new Script(
      contextResolver,
      {},
      rightIO(() => {
        Script.logger.info(initialMessage)
      })
    )

  /**
   * Method that eases the creation of steps. It accepts a message to be shown in the spinner and a
   * function that receives the context and the input from the previous step.
   * From this function you must return a Promise that resolves with the value that you want to pass to the next step (if any)
   *
   * @param message Message to initialize the spinner
   * @param action Function that receives the config object and performs an action
   */
  public step = (message: string, action: (ctx: TContext) => Promise<void>): Script<TContext> =>
    new Script(
      this.contextResolver,
      this.errorHandlers,
      pipe(
        this.action,
        chain(() => ask<TContext, Error>()),
        chain((ctx) =>
          fromTaskEither(
            tryCatch(
              async () => {
                Script.logger.start(message)
                await action(ctx)
                Script.logger.succeed(message)
              },
              (err) => err as Error
            )
          )
        )
      )
    )

  /**
   * Function to determine next action depending on passed boolean condition
   * If condition is true, step will be skipped and info action will be called.
   * Otherwise, step method will be called as usual.
   *
   * @param skipCondition When true, the action is skipped
   * @param message Message to initialize the spinner
   * @param action Function that receives the config object and performs an action
   */
  public optionalStep = (
    skipCondition: boolean,
    message: string,
    action: (ctx: TContext) => Promise<void>
  ): Script<TContext> => {
    if (skipCondition) return this.info(Brand.mellancholize(`Skipping: ${message}`))
    return this.step(message, action)
  }

  /**
   * Convenience method to generate a step that just prints a message with a small blue `i` character in front of it.
   *
   * @param message Message to be shown in the CLI
   */
  public info = (message: string): Script<TContext> =>
    new Script(
      this.contextResolver,
      this.errorHandlers,
      pipe(
        this.action,
        chain(() =>
          rightIO(() => {
            Script.logger.info(message)
          })
        )
      )
    )

  /**
   * Add a handler to catch a specific type of errors
   *
   * @param errorType the kind of errors that the handler will catch
   * @param handler handler for the errors
   */
  public catch = (errorType: string, handler: (_: Error) => string): Script<TContext> => {
    const newHandlers = { ...this.errorHandlers }
    newHandlers[errorType] = handler
    return new Script(this.contextResolver, newHandlers, this.action)
  }

  /**
   * Function to finish the script,
   * it will handle any error in between that might happen in
   * some steps.
   */
  public async done(): Promise<void> {
    try {
      const context = await this.contextResolver
      const result = await run(this.action, context)
      return pipe(
        result,
        fold((err) => {
          throw err
        }, constVoid)
      )
    } catch (err) {
      const defaultHandler = (e: Error): string => e.stack || e.message || JSON.stringify(e)
      const handler = this.errorHandlers[err.name] || defaultHandler
      throw new Error(handler(err))
    }
  }

  private static logger = oraLogger
}
