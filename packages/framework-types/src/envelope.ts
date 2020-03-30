import { CommandInterface, EntityInterface, EventInterface, UUID } from './concepts'

/**
 * An `Envelope` carries a command/event body together with the name
 * of its class. This is important information for the `Distributor` to
 * work. Each provider has to implement their own `Envelope`.
 */
export interface Envelope {
  currentUser?: UserEnvelope
  requestID: string
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

export interface ReadModelRequestEnvelope extends Envelope {
  typeName: string
  version: number
  readModelID?: UUID
}

export interface GraphQLRequestEnvelope extends Envelope {
  value?: string
  connectionID?: string
  eventType: 'CONNECT' | 'MESSAGE' | 'DISCONNECT'
}

export interface UserEnvelope {
  email: string
  roles: Array<string>
}
