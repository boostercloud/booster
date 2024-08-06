/* eslint-disable @typescript-eslint/ban-ts-comment */
import { GlobalErrorHandler } from '@boostercloud/framework-core'
import {
  CommandEnvelope,
  CommandMetadata,
  EntityInterface,
  EntitySnapshotEnvelope,
  EventEnvelope,
  EventInterface,
  NotificationInterface,
  ProjectionMetadata,
  QueryEnvelope,
  ReadModelInterface,
  ReducerMetadata,
  ScheduledCommandEnvelope,
  ScheduledCommandMetadata,
} from '@boostercloud/framework-types'
import {
  commandHandlerBeforeErrorCartId,
  commandHandlerErrorCartId,
  commandHandlerErrorIgnoredCartId,
  dispatchEventErrorCartId,
  eventErrorCartId,
  ignoreEventErrorCartId,
  projectionErrorCartId,
  queryHandlerErrorCartId,
  reducerErrorCartId,
} from '../constants'

@GlobalErrorHandler()
export class AppErrorHandler {
  public static async onCommandHandlerError(
    error: Error,
    commandEnvelope: CommandEnvelope,
    commandMetadata: CommandMetadata
  ): Promise<Error | undefined> {
    console.log(commandEnvelope)
    console.log(commandMetadata)
    if (commandEnvelope.value.cartId === commandHandlerErrorIgnoredCartId) {
      return undefined
    }
    if (commandEnvelope.value.cartId === commandHandlerErrorCartId) {
      return new Error(`${error.message}-onCommandHandlerError with metadata: ${JSON.stringify(commandMetadata)}`)
    }
    if (commandEnvelope.value.cartId === commandHandlerBeforeErrorCartId) {
      return new Error(`${error.message}-onBeforeCommandHandlerError with metadata: ${JSON.stringify(commandMetadata)}`)
    }
    return error
  }

  public static async onQueryHandlerError(error: Error, query: QueryEnvelope): Promise<Error | undefined> {
    if (query.value.cartId === queryHandlerErrorCartId) {
      return new Error(error.message + '-onQueryHandlerError')
    }
    return error
  }

  public static async onScheduledCommandHandlerError(
    error: Error,
    scheduledCommandEnvelope: ScheduledCommandEnvelope,
    scheduledCommandMetadata: ScheduledCommandMetadata
  ): Promise<Error | undefined> {
    console.log('onScheduledCommandHandlerError')
    console.log(scheduledCommandEnvelope)
    console.log(scheduledCommandMetadata)
    return new Error(
      `${error.message}-onScheduledCommandHandlerError with metadata: ${JSON.stringify(scheduledCommandMetadata)}`
    )
  }

  public static async onDispatchEventHandlerError(
    error: Error,
    eventEnvelope: EventEnvelope | NotificationInterface,
    eventHandlerMetadata: unknown,
    eventInstance: EventInterface
  ): Promise<Error | undefined> {
    console.log(eventEnvelope)
    console.log(eventHandlerMetadata)
    const entityId = eventInstance?.entityID ? eventInstance.entityID() : ''
    if (entityId === dispatchEventErrorCartId) {
      return new Error(
        `${error.message}-onDispatchEventHandlerError with metadata: ${JSON.stringify(eventHandlerMetadata)}`
      )
    }
    return error
  }

  public static async onReducerError(
    error: Error,
    eventEnvelope: EventEnvelope,
    reducerMetadata: ReducerMetadata,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error> {
    console.log(eventEnvelope)
    console.log(reducerMetadata)
    console.log(snapshotInstance)
    const entityId = eventInstance?.entityID ? eventInstance.entityID() : ''
    if (entityId === reducerErrorCartId) {
      return new Error(`${error.message}-onReducerError with metadata: ${JSON.stringify(reducerMetadata)}`)
    }
    return error
  }

  public static async onProjectionError(
    error: Error,
    entityEnvelope: EntitySnapshotEnvelope,
    projectionMetadata: ProjectionMetadata<EntityInterface, ReadModelInterface>,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined> {
    console.log(entityEnvelope)
    console.log(projectionMetadata)
    console.log(readModel)
    if (entity?.id === projectionErrorCartId) {
      return new Error(`${error.message}-onProjectionError with metadata: ${JSON.stringify(projectionMetadata)}`)
    }
    return error
  }

  public static async onEventError(error: Error, eventEnvelope: EventEnvelope): Promise<Error | undefined> {
    if (eventEnvelope.entityID === eventErrorCartId) {
      return new Error(error.message + '-onEventError')
    }
    if (eventEnvelope.entityID === ignoreEventErrorCartId) {
      return undefined
    }
    return error
  }

  public static async onError(error: Error | undefined): Promise<Error | undefined> {
    return new Error(error?.message + '-onError')
  }
}
