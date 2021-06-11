import { ReadModelEnvelope } from '@boostercloud/framework-types'
import { random } from 'faker'

export function createMockReadModelEnvelope(): ReadModelEnvelope {
  return createMockReadModelEnvelopeWithTypeName(random.word())
}

export function createMockReadModelEnvelopeWithTypeName(typeName: string): ReadModelEnvelope {
  return {
    value: {
      id: random.uuid(),
      age: random.number(40),
      foo: random.word(),
      bar: random.float(),
    },
    typeName: typeName,
  }
}
