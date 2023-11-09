import {
  CommandInput,
  EntityInterface,
  EventInterface,
  NotificationInterface,
  ReadModelInterface,
  SequenceKey,
  UUID,
} from './concepts'
import { GraphQLClientMessage } from './graphql-websocket-messages'
import { FilterFor, SortFor } from './searcher'
import { Class } from './typelevel'

/**
 * An `Envelope` carries a command/event body together with the name
 * of its class. This is important information for the `Distributor` to
 * work. Each provider has to implement their own `Envelope`.
 */
export interface Envelope {
  currentUser?: UserEnvelope
  requestID: UUID
  context?: ContextEnvelope
}

export interface TypedEnvelope extends Envelope {
  typeName: string
  version: number
}

export interface CommandEnvelope extends TypedEnvelope {
  value: CommandInput
}

export type QueryEnvelope = CommandEnvelope

export interface HealthEnvelope extends Envelope {
  componentPath: string
  token?: string
}

export interface ScheduledCommandEnvelope extends Envelope {
  typeName: string
}

export type SuperKindType = 'domain' | 'notification' | 'booster'

export interface EventStoreEntryEnvelope extends TypedEnvelope {
  superKind: SuperKindType
  entityID: UUID
  entityTypeName: string
  value: EventInterface | EntityInterface | NotificationInterface
}

export interface NonPersistedEventEnvelope extends EventStoreEntryEnvelope {
  kind: 'event'
}

export interface EventEnvelope extends NonPersistedEventEnvelope {
  createdAt: string
}

export interface NonPersistedEntitySnapshotEnvelope extends EventStoreEntryEnvelope {
  kind: 'snapshot'
  snapshottedEventCreatedAt: string
}

export interface EntitySnapshotEnvelope extends NonPersistedEntitySnapshotEnvelope {
  /** Logic creation date of the snapshot, it always matches the creation date of the latest event included in it. */
  createdAt: string
  /** Time when this snapshot was actually persisted in the database. */
  persistedAt: string
}
export interface EventSearchRequest extends Envelope {
  parameters: EventSearchParameters
}

export type EventSearchParameters = EventParametersFilterByEntity | EventParametersFilterByType

export interface EventLimitParameter {
  limit?: number
}

export interface EventTimeParameterFilter extends EventLimitParameter {
  from?: string
  to?: string
}

export interface EventParametersFilterByEntity extends EventTimeParameterFilter {
  entity: string
  entityID?: string
}

export interface EventParametersFilterByType extends EventTimeParameterFilter {
  type: string
}

export interface EventSearchResponse {
  type: string
  entity: string
  entityID: UUID
  requestID: UUID
  user?: UserEnvelope
  createdAt: string
  value: EventInterface | NotificationInterface
}

export interface ReadModelEnvelope {
  typeName: string
  value: ReadModelInterface
}

export interface PaginatedEntityIdResult {
  entityID: UUID
}

export interface PaginatedEntitiesIdsResult {
  items: Array<PaginatedEntityIdResult>
  count?: number
  cursor?: Record<string, string>
}

export interface ReadModelListResult<TReadModel> {
  items: Array<TReadModel>
  count?: number
  cursor?: Record<string, string>
}

export interface ReadModelRequestEnvelope<TReadModel extends ReadModelInterface> extends Envelope {
  key?: {
    id: UUID
    sequenceKey?: SequenceKey
  }
  class: Class<TReadModel>
  className: string
  version: number
  filters: ReadModelRequestProperties<TReadModel>
  sortBy?: ReadModelSortProperties<TReadModel>
  limit?: number
  afterCursor?: unknown
  paginatedVersion?: boolean // Used only for retrocompatibility
}

export interface ReadModelRequestArgs<TReadModel extends ReadModelInterface> {
  filter?: ReadModelRequestProperties<TReadModel>
  sortBy?: ReadModelSortProperties<TReadModel>
  limit?: number
  afterCursor?: unknown
}

export interface ReadModelByIdRequestArgs {
  id: string
  [sequenceKey: string]: string | undefined
}

export type ReadModelRequestProperties<TReadModel> = Record<string, FilterFor<TReadModel>>

export type ReadModelSortProperties<TReadModel> = Record<string, SortFor<TReadModel>>

export type EventType = 'CONNECT' | 'MESSAGE' | 'DISCONNECT'

export interface GraphQLRequestEnvelope extends Envelope {
  eventType: EventType
  connectionID?: string
  value?: GraphQLOperation | GraphQLClientMessage
  token?: string
}
export type GraphQLRequestEnvelopeError = Pick<GraphQLRequestEnvelope, 'eventType' | 'connectionID' | 'requestID'> & {
  error: Error
}

export interface SubscriptionEnvelope extends ReadModelRequestEnvelope<ReadModelInterface> {
  expirationTime: number // In Epoch format
  connectionID: string
  operation: GraphQLOperation
}

export interface GraphQLOperation {
  query: string
  id?: string
  operationName?: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  variables?: Record<string, any>
}

export interface ConnectionDataEnvelope {
  expirationTime: number // In Epoch format
  user?: UserEnvelope
}

export interface UserEnvelope {
  /** An optional identifier of the user */
  id?: string
  /** The unique username of the current user */
  username: string
  /** The list of role names assigned to this user */
  roles: Array<string>
  /** An object containing the claims included in the body of the JWT token */
  claims: Record<string, unknown>
  /** An object containing the headers of the JWT token for further verification */
  header?: Record<string, unknown>
}

export interface ContextEnvelope {
  /** Decoded request header and body */
  request: {
    headers: unknown
    body: unknown
  }
  /** Provider-dependent raw request context object */
  rawContext: unknown
}
