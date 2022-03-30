/* eslint-disable @typescript-eslint/ban-ts-comment */
import { GlobalErrorHandler } from '@boostercloud/framework-core'
import { CommandEnvelope, EntityInterface, EventInterface, ReadModelInterface } from '@boostercloud/framework-types'
import {
  commandHandlerBeforeErrorCartId,
  commandHandlerErrorCartId,
  dispatchEventErrorCartId,
  projectionErrorCartId,
  reducerErrorCartId,
} from '../constants'

@GlobalErrorHandler()
export class AppErrorHandler {
  public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error> {
    if (command.value.cartId === commandHandlerErrorCartId) {
      return new Error(error.message + '-onCommandHandlerError')
    }
    if (command.value.cartId === commandHandlerBeforeErrorCartId) {
      return new Error(error.message + '-onBeforeCommandHandlerError')
    }
    return error
  }

  public static async onScheduledCommandHandlerError(error: Error): Promise<Error> {
    console.log('onScheduledCommandHandlerError')
    return error
  }

  public static async onDispatchEventHandlerError(error: Error, eventInstance: EventInterface): Promise<Error> {
    if (eventInstance?.entityID() === dispatchEventErrorCartId) {
      return new Error(error.message + '-onDispatchEventHandlerError')
    }
    return error
  }

  public static async onReducerError(
    error: Error,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error> {
    if (eventInstance?.entityID() === reducerErrorCartId) {
      return new Error(error.message + '-onReducerError')
    }
    return error
  }

  public static async onProjectionError(
    error: Error,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error> {
    if (entity?.id === projectionErrorCartId) {
      return new Error(error.message + '-onProjectionError')
    }
    return error
  }

  public static async onError(error: Error): Promise<Error> {
    return new Error(error.message + '-onError')
  }
}
