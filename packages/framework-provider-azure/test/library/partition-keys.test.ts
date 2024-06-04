import { partitionKeyForEvent, partitionKeyForSnapshot } from '../../src/library/partition-keys'
import { expect } from '../expect'

describe('Azure keys helpers', () => {
  describe('partitionKeyForEvent', () => {
    it('should return the correct partition key for an event', () => {
      expect(partitionKeyForEvent('User', '123')).to.be.equal('User-123-event')
    })
  })

  describe('partitionKeyForSnapshot', () => {
    it('should return the correct partition key for an entity snapshot', () => {
      expect(partitionKeyForSnapshot('User', '123')).to.be.equal('User-123-snapshot')
    })
  })
})
