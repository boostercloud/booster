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
  })
})
