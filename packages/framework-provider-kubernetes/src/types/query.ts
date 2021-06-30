import { UUID, EventEnvelope } from '@boostercloud/framework-types'

export enum EntityType {
  Event = 'event',
  Snapshot = 'snapshot',
  ReadModel = 'readmodel',
}
export interface Query {
  type: EntityType
  typeName: string
  id?: UUID
  keyPredicate: (key: string) => boolean
  valuePredicate: (envelope: EventEnvelope) => boolean
  sortBy: (a: EventEnvelope, b: EventEnvelope) => number
}
