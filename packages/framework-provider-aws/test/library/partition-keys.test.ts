/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import {
  partitionKeyForEvent,
  partitionKeyForEntitySnapshot,
  sortKeyForSubscription,
} from '../../src/library/keys-helper'
import { lorem, random } from 'faker'

describe('"partitionKeyForEvent" function', () => {
  it('returns a correctly formatted partition key', () => {
    const entityName = lorem.word()
    const entityID = random.uuid()

    const expected = `${entityName}-${entityID}-event`
    const got = partitionKeyForEvent(entityName, entityID)

    expect(got).to.equal(expected)
  })
})

describe('"partitionKeyForEntitySnapshot" function', () => {
  it('returns a correctly formatted partition key', () => {
    const entityName = lorem.word()
    const entityID = random.uuid()

    const expected = `${entityName}-${entityID}-snapshot`
    const got = partitionKeyForEntitySnapshot(entityName, entityID)

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
