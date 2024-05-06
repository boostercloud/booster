import { AnyClass } from '../typelevel'
import { CommandEnvelope, NonPersistedEntitySnapshotEnvelope, EntitySnapshotEnvelope, EventEnvelope, ScheduledCommandEnvelope, QueryEnvelope } from '../envelope'
import { ReadModelInterface } from './read-model'
import { EntityInterface } from './entity'
import { ReducerMetadata } from './reducer-metadata'
import { ScheduledCommandMetadata } from './scheduled-command'
import { CommandMetadata } from './command'
import { ProjectionMetadata } from './projection-metadata'
import { NotificationInterface } from './notification'
import { EventInterface } from './event'

export interface GlobalErrorHandlerInterface extends AnyClass {
  onCommandHandlerError?(
    error: Error,
    commandEnvelope: CommandEnvelope,
    commandMetadata: CommandMetadata
  ): Promise<Error | undefined>
  onQueryHandlerError?(error: Error, query: QueryEnvelope): Promise<Error | undefined>
  onScheduledCommandHandlerError?(
    error: Error,
    scheduledCommandEnvelope: ScheduledCommandEnvelope,
    scheduledCommandMetadata: ScheduledCommandMetadata
  ): Promise<Error | undefined>
  onDispatchEventHandlerError?(
    error: Error,
    eventEnvelope: EventEnvelope | NotificationInterface,
    eventHandlerMetadata: unknown,
    eventInstance: EventInterface
  ): Promise<Error | undefined>
  onReducerError?(
    error: Error,
    eventEnvelope: EventEnvelope,
    reducerMetadata: ReducerMetadata,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error>
  onProjectionError?(
    error: Error,
    entityEnvelope: EntitySnapshotEnvelope,
    projectionMetadata: ProjectionMetadata<EntityInterface>,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined>
  onSnapshotPersistError?(error: Error, snapshot: NonPersistedEntitySnapshotEnvelope): Promise<Error | undefined>
  onError?(error: Error | undefined): Promise<Error | undefined>
}

export interface GlobalErrorHandlerMetadata {
  readonly class: GlobalErrorHandlerInterface
}
