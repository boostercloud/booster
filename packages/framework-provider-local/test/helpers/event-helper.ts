import { EntitySnapshotEnvelope, EventEnvelope, NonPersistedEventEnvelope } from '@boostercloud/framework-types'
import { random, date } from 'faker'

export function createMockNonPersistedEventEnvelop(): NonPersistedEventEnvelope {
  return createMockNonPersistedEventEnvelopeForEntity(random.word(), random.uuid())
}

export function createMockNonPersistedEventEnvelopeForEntity(
  entityTypeName: string,
  entityID: string
): NonPersistedEventEnvelope {
  return {
    kind: 'event',
    superKind: 'domain',
    entityID: entityID,
    entityTypeName: entityTypeName,
    value: {
      id: random.uuid(),
    },
    requestID: random.uuid(),
    typeName: random.word(),
    version: random.number(),
  }
}

export function createMockEventEnvelope(): EventEnvelope {
  return createMockEventEnvelopeForEntity(random.word(), random.uuid())
}

export function createMockEventEnvelopeForEntity(entityTypeName: string, entityID: string): EventEnvelope {
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

export function createMockEntitySnapshotEnvelope(entityTypeName?: string, entityId?: string): EntitySnapshotEnvelope {
  const creationDate = date.past()
  const snapshottedEventCreatedAt = creationDate.toISOString()
  return {
    kind: 'snapshot',
    superKind: 'domain',
    entityID: entityId ?? random.uuid(),
    entityTypeName: entityTypeName ?? random.word(),
    value: {
      id: random.uuid(),
    },
    createdAt: date.past().toISOString(),
    requestID: random.uuid(),
    typeName: random.word(),
    version: random.number(),
    snapshottedEventCreatedAt,
  }
}
