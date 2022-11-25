/* eslint-disable @typescript-eslint/ban-ts-comment */
import { GlobalErrorHandler } from '@boostercloud/framework-core'
import {
  CommandEnvelope,
  EntityInterface,
  EventInterface,
  QueryEnvelope,
  ReadModelInterface,
} from '@boostercloud/framework-types'
import {
  commandHandlerBeforeErrorCartId,
  commandHandlerErrorCartId,
  commandHandlerErrorIgnoredCartId,
  dispatchEventErrorCartId,
  projectionErrorCartId,
  queryHandlerErrorCartId,
  queryHandlerErrorIgnoredCartId,
  reducerErrorCartId,
} from '../constants'

@GlobalErrorHandler()
export class AppErrorHandler {
  public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {
    if (command.value.cartId === commandHandlerErrorIgnoredCartId) {
      return undefined
    }
    if (command.value.cartId === commandHandlerErrorCartId) {
      return new Error(error.message + '-onCommandHandlerError')
    }
    if (command.value.cartId === commandHandlerBeforeErrorCartId) {
      return new Error(error.message + '-onBeforeCommandHandlerError')
    }
    return error
  }

  public static async onQueryHandlerError(error: Error, query: QueryEnvelope): Promise<Error | undefined> {
    if (query.value.cartId === queryHandlerErrorIgnoredCartId) {
      return undefined
    }
    if (query.value.cartId === queryHandlerErrorCartId) {
      return new Error(error.message + '-onQueryHandlerError')
    }
    return error
  }

  public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {
    console.log('onScheduledCommandHandlerError')
    return error
  }

  public static async onDispatchEventHandlerError(
    error: Error,
    eventInstance: EventInterface
  ): Promise<Error | undefined> {
    if (eventInstance?.entityID() === dispatchEventErrorCartId) {
      return new Error(error.message + '-onDispatchEventHandlerError')
    }
    return error
  }

  public static async onReducerError(
    error: Error,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error | undefined> {
    if (eventInstance?.entityID() === reducerErrorCartId) {
      return new Error(error.message + '-onReducerError')
    }
    return error
  }

  public static async onProjectionError(
    error: Error,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined> {
    if (entity?.id === projectionErrorCartId) {
      return new Error(error.message + '-onProjectionError')
    }
    return error
  }

  public static async onError(error: Error | undefined): Promise<Error | undefined> {
    return new Error(error?.message + '-onError')
  }
}
