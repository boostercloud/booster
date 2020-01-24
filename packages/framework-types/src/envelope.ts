import { CommandInterface, EntityInterface, EventInterface } from './concepts'

/**
 * An `Envelope` carries a command/event body together with the name
 * of its class. This is important information for the `Distributor` to
 * work. Each provider has to implement their own `Envelope`.
 */
export interface Envelope {
  currentUser?: UserEnvelope
  requestID: string
  typeName: string
  version: number
}

export interface CommandEnvelope extends Envelope {
  value: CommandInterface
}

export interface EventEnvelope extends Envelope {
  kind: 'event' | 'snapshot'
  entityID: string
  entityTypeName: string
  value: EventInterface | EntityInterface
  createdAt: string
}

export interface UserEnvelope {
  email: string
  roles: Array<string>
}
