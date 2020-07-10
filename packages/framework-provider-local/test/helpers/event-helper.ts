import { EventEnvelope } from '@boostercloud/framework-types/dist/envelope'
import { random, date } from 'faker'

export function createMockEvent(): EventEnvelope {
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