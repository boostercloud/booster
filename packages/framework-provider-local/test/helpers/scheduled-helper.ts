import { random } from 'faker'

interface LocalScheduleCommandEnvelope {
  typeName: string
}

export function createMockLocalScheduleCommandEnvelope(): Partial<LocalScheduleCommandEnvelope> {
  return {
    typeName: random.word()
  }
}
