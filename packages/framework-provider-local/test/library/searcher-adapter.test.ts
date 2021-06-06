import { expect } from '../expect'
import { queryRecordFor } from '../../src/library/searcher-adapter'

describe('searcher-adapter', () => {
  const typeName = 'SomeReadModel'
  describe('converts simple operators', () => {
    it('converts the "eq" operator', () => {
      const result = queryRecordFor(typeName, { field: { eq: 'one' } })
      expect(result).to.deep.equal({ 'value.field': 'one', typeName })
    })

    it('converts the "ne" operator', () => {
      const result = queryRecordFor(typeName, { field: { ne: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $ne: 'one' }, typeName })
    })

    it('converts the "lt" operator', () => {
      const result = queryRecordFor(typeName, { field: { lt: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $lt: 1 }, typeName })
    })

    it('converts the "gt" operator', () => {
      const result = queryRecordFor(typeName, { field: { gt: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $gt: 1 }, typeName })
    })

    it('converts the "lte" operator', () => {
      const result = queryRecordFor(typeName, { field: { lte: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $lte: 1 }, typeName })
    })

    it('converts the "gte" operator', () => {
      const result = queryRecordFor(typeName, { field: { gte: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $gte: 1 }, typeName })
    })

    it('converts the "in" operator', () => {
      const result = queryRecordFor(typeName, { field: { in: ['one', 'two'] } })
      expect(result).to.deep.equal({ 'value.field': { $in: ['one', 'two'] }, typeName })
    })
  })
  describe('converts operators that rely on regexes', () => {
    it('converts the "contains" operator', () => {
      const result = queryRecordFor(typeName, { field: { contains: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') }, typeName })
    })

    it('converts the "includes" operator', () => {
      const result = queryRecordFor(typeName, { field: { includes: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') }, typeName })
    })

    it('converts the "beginsWith" operator', () => {
      const result = queryRecordFor(typeName, { field: { beginsWith: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('^one') }, typeName })
    })
  })
})
