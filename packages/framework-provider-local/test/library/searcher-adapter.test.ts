import { expect } from '../expect'
import { queryRecordFor } from '../../src/library/searcher-adapter'

describe('searcher-adapter', () => {
  const typeName = 'SomeReadModel'
  describe('filter conversion for keys to NeDB queries', () => {
    it('converts the "=" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: '=', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': 'one', typeName })
    })

    it('converts the "!=" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: '!=', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $ne: 'one' }, typeName })
    })

    it('converts the "<" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: '<', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $lt: 'one' }, typeName })
    })

    it('converts the ">" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: '>', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $gt: 'one' }, typeName })
    })

    it('converts the "<=" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: '<=', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $lte: 'one' }, typeName })
    })

    it('converts the ">=" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: '>=', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $gte: 'one' }, typeName })
    })

    it('converts the "in" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: 'in', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $in: ['one'] }, typeName })
    })

    it('converts the "between" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: 'between', values: ['one', 'two'] } })
      expect(result).to.deep.equal({ 'value.field': { $gt: 'one', $lte: 'two' }, typeName })
    })
  })
})
