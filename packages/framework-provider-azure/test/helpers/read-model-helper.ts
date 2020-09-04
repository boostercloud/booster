import { ReadModelInterface } from '@boostercloud/framework-types'
import { random } from 'faker'

export function createMockReadModel(): ReadModelInterface {
  return {
    id: random.uuid(),
    some: 'object',
  }
}
