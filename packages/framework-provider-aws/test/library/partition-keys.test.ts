/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { partitionKeyForEvent, sortKeyForSubscription } from '../../src/library/keys-helper'
import { EventEnvelope } from '@boostercloud/framework-types'
import { lorem, random } from 'faker'

describe('"partitionKeyForEvent" function', () => {
  it('returns a correctly formatted partition key', () => {
    const entityName = lorem.word()
    const entityID = random.uuid()
    const kind: EventEnvelope['kind'] = 'snapshot'

    const expected = `${entityName}-${entityID}-${kind}`
    const got = partitionKeyForEvent(entityName, entityID, kind)

    expect(got).to.equal(expected)
  })
})

describe('"sortKeyForSubscription" function', () => {
  it('returns a correctly formatted sortKey key', () => {
    const connectionID = random.uuid()
    const subscriptionID = random.uuid()

    const expected = `${connectionID}-${subscriptionID}`
    const got = sortKeyForSubscription(connectionID, subscriptionID)

    expect(got).to.equal(expected)
  })
})
