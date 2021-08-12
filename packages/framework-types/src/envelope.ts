import { CommandInput, EntityInterface, EventInterface, ReadModelInterface, SequenceKey, UUID } from './concepts'
import { GraphQLClientMessage } from './graphql-websocket-messages'
import { FilterFor } from './searcher'

/**
 * An `Envelope` carries a command/event body together with the name
 * of its class. This is important information for the `Distributor` to
 * work. Each provider haimport { SequenceKey } from './concepts/sequence-metadata';
s to implement their own `Envelope`.
 */
export interface Envelope {
  currentUser?: UserEnvelope
  requestID: UUID
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
  filters: EventFilter
}

export type EventFilter = EventFilterByEntity | EventFilterByType

export interface EventTimeFilter {
  from?: string
  to?: string
}

export interface EventFilterByEntity extends EventTimeFilter {
  entity: string
  entityID?: string
}

export interface EventFilterByType extends EventTimeFilter {
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

export interface ReadModelRequestEnvelope extends Envelope {
  typeName: string
  version: number
  filters: ReadModelRequestProperties
  limit?: number
  afterCursor?: unknown
  paginatedVersion?: boolean // Used only for retrocompatibility
}

export interface ReadModelByIdRequestEnvelope extends Envelope {
  typeName: string
  version: number
  id: string
  sequenceKey?: SequenceKey
}

export interface ReadModelRequestArgs {
  filter?: ReadModelRequestProperties
  limit?: number
  afterCursor?: unknown
}

export interface ReadModelByIdRequestArgs {
  id: string
  [sequenceKey: string]: string | undefined
}

export type ReadModelPropertyFilter = FilterFor<unknown>

export type ReadModelRequestProperties = Record<string, ReadModelPropertyFilter>

export interface GraphQLRequestEnvelope extends Envelope {
  eventType: 'CONNECT' | 'MESSAGE' | 'DISCONNECT'
  connectionID?: string
  value?: GraphQLOperation | GraphQLClientMessage
  token?: string
}
export type GraphQLRequestEnvelopeError = Pick<GraphQLRequestEnvelope, 'eventType' | 'connectionID' | 'requestID'> & {
  error: Error
}

export interface SubscriptionEnvelope extends ReadModelRequestEnvelope {
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
  role: string
  claims: Record<string, unknown>
}
