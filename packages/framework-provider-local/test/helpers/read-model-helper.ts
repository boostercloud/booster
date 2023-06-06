import { ReadModelEnvelope } from '@boostercloud/framework-types'
import { random } from 'faker'
import { expect } from '../expect'

export function createMockReadModelEnvelope(): ReadModelEnvelope {
  return {
    value: {
      id: random.uuid(),
      age: random.number(40),
      foo: random.word(),
      bar: random.float(),
      boosterMetadata: {
        version: 1,
        schemaVersion: 1,
      },
    },
    typeName: random.word(),
  }
}

export function assertOrderByAgeDesc(result: Array<ReadModelEnvelope>): void {
  const readModelEnvelopes = [...result] as Array<ReadModelEnvelope>
  const expectedResult = readModelEnvelopes.sort(function (a: ReadModelEnvelope, b: ReadModelEnvelope) {
    return a.value.age > b.value.age ? -1 : 1
  })

  expect(result).to.eql(expectedResult)
}

export function assertOrderByAgeAndIdDesc(result: Array<ReadModelEnvelope>): void {
  const readModelEnvelopes = [...result] as Array<ReadModelEnvelope>
  const expectedResult = readModelEnvelopes.sort(function (a: ReadModelEnvelope, b: ReadModelEnvelope) {
    if (a.value.age === b.value.age) {
      return a.value.id > b.value.id ? -1 : 1
    }
    return a.value.age > b.value.age ? -1 : 1
  })

  expect(result).to.eql(expectedResult)
}
