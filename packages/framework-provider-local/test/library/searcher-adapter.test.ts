import { expect } from '../expect'
import { queryRecordFor } from '../../src/library/searcher-adapter'

describe('searcher-adapter', () => {
  const typeName = 'SomeReadModel'
  describe('converts simple operators', () => {
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
  describe('converts operators that rely on regexes', () => {
    it('converts the "contains" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: 'contains', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') }, typeName })
    })

    it('converts the "not-contains" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: 'not-contains', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('^((?!one).)*$', 'gm') }, typeName })
    })

    it('converts the "begins-with" operator', () => {
      const result = queryRecordFor(typeName, { field: { operation: 'begins-with', values: ['one'] } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('^one') }, typeName })
    })
  })
})
