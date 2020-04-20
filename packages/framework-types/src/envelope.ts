import { CommandInterface, EntityInterface, EventInterface, ReadModelInterface, UUID } from './concepts'
import { DocumentNode } from 'graphql'

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
  /** @deprecated */
  readModelID?: UUID
}

export interface ReadModelPropertyFilter {
  operation: string
  values: Array<any>
}

export interface GraphQLRequestEnvelope extends Envelope {
  eventType: 'CONNECT' | 'MESSAGE' | 'DISCONNECT'
  connectionID?: string
  value?: string
  variables?: Record<string, any>
}

export interface SubscriptionEnvelope extends ReadModelRequestEnvelope {
  expirationTime: number // In Epoch format
  connectionID: string
  operation: GraphQLOperation
}

export interface GraphQLOperation {
  query: DocumentNode
  variables?: Record<string, any>
}

export interface UserEnvelope {
  email: string
  roles: Array<string>
}
