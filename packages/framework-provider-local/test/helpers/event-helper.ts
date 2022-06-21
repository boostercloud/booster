import { EventEnvelope } from '@boostercloud/framework-types'
import { random, date } from 'faker'

export function createMockEventEnvelop(): EventEnvelope {
  return createMockEventEnvelopForEntity(random.word(), random.uuid())
}

export function createMockEventEnvelopForEntity(entityTypeName: string, entityID: string): EventEnvelope {
  return {
    kind: 'event',
    superKind: 'domain',
    entityID: entityID,
    entityTypeName: entityTypeName,
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
    superKind: 'domain',
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
