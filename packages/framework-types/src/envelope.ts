import { CommandInput, EntityInterface, EventInterface, ReadModelInterface, SequenceKey, UUID } from './concepts'
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

export interface CommandEnvelope extends Envelope {
  typeName: string
  version: number
  value: CommandInput
}

export interface ScheduledCommandEnvelope extends Envelope {
  typeName: string
}

export interface EventEnvelope extends Envelope {
  typeName: string
  version: number
  kind: 'event' | 'snapshot'
  entityID: UUID
  entityTypeName: string
  value: EventInterface | EntityInterface
  createdAt: string
  snapshottedEventCreatedAt?: string
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
  value: EventInterface
}

export interface ReadModelEnvelope {
  typeName: string
  value: ReadModelInterface
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

export interface GraphQLRequestEnvelope extends Envelope {
  eventType: 'CONNECT' | 'MESSAGE' | 'DISCONNECT'
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
  id?: string
  username: string
  roles: Array<string>
  claims: Record<string, unknown>
  header?: Record<string, unknown>
}

export interface ContextEnvelope {
  request: {
    headers: unknown
    body: unknown
  }
  rawContext: unknown
}
