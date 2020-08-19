import { CommandInterface, EntityInterface, EventInterface, ReadModelInterface, UUID } from './concepts'
import { GraphQLClientMessage } from './graphql-websocket-messages'

/**
 * An `Envelope` carries a command/event body together with the name
 * of its class. This is important information for the `Distributor` to
 * work. Each provider has to implement their own `Envelope`.
 */
export interface Envelope {
  currentUser?: UserEnvelope
  requestID: UUID
}

export interface CommandEnvelope extends Envelope {
  typeName: string
  version: number
  value: CommandInterface
}

export interface EventEnvelope extends Envelope {
  typeName: string
  version: number
  kind: 'event' | 'snapshot'
  entityID: UUID
  entityTypeName: string
  value: EventInterface | EntityInterface
  createdAt: string
}

export interface ReadModelEnvelope {
  typeName: string
  value: ReadModelInterface
}

export interface ReadModelRequestEnvelope extends Envelope {
  typeName: string
  version: number
  filters?: Record<string, ReadModelPropertyFilter>
}

export interface ReadModelPropertyFilter {
  operation: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  values: Array<any>
}

export interface GraphQLRequestEnvelope extends Envelope {
  eventType: 'CONNECT' | 'MESSAGE' | 'DISCONNECT'
  connectionID?: string
  value?: GraphQLOperation | GraphQLClientMessage
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

export interface UserEnvelope {
  username: string
  role: string
}
