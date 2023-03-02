import {
  partitionKeyForEvent,
  partitionKeyForEntitySnapshot,
  partitionKeyForIndexByEntity,
  sortKeyForSubscription,
} from '../../src/library/keys-helper'
import { expect } from '../expect'

describe('AWS keys helpers', () => {
  describe('partitionKeyForEvent', () => {
    it('should return the correct partition key for an event', () => {
      expect(partitionKeyForEvent('User', '123')).to.be.equal('User-123-event')
    })
  })

  describe('partitionKeyForEntitySnapshot', () => {
    it('should return the correct partition key for an entity snapshot', () => {
      expect(partitionKeyForEntitySnapshot('User', '123')).to.be.equal('User-123-snapshot')
    })
  })

  describe('partitionKeyForIndexByEntity', () => {
    it('should return the correct partition key for an index by entity for an event', () => {
      expect(partitionKeyForIndexByEntity('User', 'event')).to.be.equal('User-event')
    })

    it('should return the correct partition key for an index by entity for a snapshot', () => {
      expect(partitionKeyForIndexByEntity('User', 'snapshot')).to.be.equal('User-snapshot')
    })
  })

  describe('sortKeyForSubscription', () => {
    it('should return the correct sort key for a subscription', () => {
      expect(sortKeyForSubscription('123', '456')).to.be.equal('123-456')
    })
  })
})
