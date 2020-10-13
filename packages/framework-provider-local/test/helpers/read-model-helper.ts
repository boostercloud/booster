import { ReadModelEnvelope } from '@boostercloud/framework-types/dist/envelope'
import { random } from 'faker'

export function createMockReadModelEnvelope(): ReadModelEnvelope {
  return {
    value: {
      id: random.uuid(),
    },
    typeName: random.word(),
  }
}
