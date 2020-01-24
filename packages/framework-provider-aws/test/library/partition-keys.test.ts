/* eslint-disable @typescript-eslint/no-explicit-any */
import * as chai from 'chai'
import { expect } from 'chai'
import { partitionKeyForEvent } from '../../src/library/partition-keys'
import { EventEnvelope } from '@boostercloud/framework-types'

chai.use(require('sinon-chai'))

describe('"partitionKeyForEvent" function', () => {
  it('returns a correctly formated partition key', () => {
    const entityName = 'name'
    const entityID = 'id'
    const kind: EventEnvelope['kind'] = 'snapshot'

    const expected = `${entityName}-${entityID}-${kind}`
    const got = partitionKeyForEvent(entityName, entityID, kind)

    expect(got).to.equal(expected)
  })
})
