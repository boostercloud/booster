import { EventEnvelope } from '@boostercloud/framework-types'
import { random, date } from 'faker'

export function createMockEventEnvelop(): EventEnvelope {
  return {
    kind: 'event',
    entityID: random.uuid(),
    entityTypeName: random.word(),
    value: {
      id: random.uuid(),
    },
    createdAt: date.past().toISOString(),
    requestID: random.uuid(),
    typeName: random.word(),
    version: random.number(),
  }
}

export function createMockSnapshot(): EventEnvelope {
  return {
    kind: 'snapshot',
    entityID: random.uuid(),
    entityTypeName: random.word(),
    value: {
      id: random.uuid(),
    },
    createdAt: date.past().toISOString(),
    requestID: random.uuid(),
    typeName: random.word(),
    version: random.number(),
  }
}
