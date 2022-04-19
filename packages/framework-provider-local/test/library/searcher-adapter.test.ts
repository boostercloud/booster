import { expect } from '../expect'
import { queryRecordFor } from '../../src/library/searcher-adapter'

describe('searcher-adapter', () => {
  describe('converts simple operators', () => {
    it('converts the "eq" operator', () => {
      const result = queryRecordFor({ field: { eq: 'one' } })
      expect(result).to.deep.equal({ 'value.field': 'one' })
    })

    it('converts the "ne" operator', () => {
      const result = queryRecordFor({ field: { ne: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $ne: 'one' } })
    })

    it('converts the "lt" operator', () => {
      const result = queryRecordFor({ field: { lt: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $lt: 1 } })
    })

    it('converts the "gt" operator', () => {
      const result = queryRecordFor({ field: { gt: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $gt: 1 } })
    })

    it('converts the "lte" operator', () => {
      const result = queryRecordFor({ field: { lte: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $lte: 1 } })
    })

    it('converts the "gte" operator', () => {
      const result = queryRecordFor({ field: { gte: 1 } })
      expect(result).to.deep.equal({ 'value.field': { $gte: 1 } })
    })

    it('converts the "in" operator', () => {
      const result = queryRecordFor({ field: { in: ['one', 'two'] } })
      expect(result).to.deep.equal({ 'value.field': { $in: ['one', 'two'] } })
    })
  })
  describe('converts operators that rely on regexes', () => {
    it('converts the "contains" operator', () => {
      const result = queryRecordFor({ field: { contains: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') } })
    })

    it('converts the "includes" operator', () => {
      const result = queryRecordFor({ field: { includes: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('one') } })
    })

    it('converts the "includes" operator with objects', () => {
      const result = queryRecordFor({ parentField: { includes: { children1: 'abc', children2: 2 } } })
      expect(result).to.deep.equal({
        'value.parentField': { $elemMatch: { children1: 'abc', children2: 2 } },
      })
    })

    it('converts nested operator with objects', () => {
      const result = queryRecordFor({ parentField: { children1: { eq: 'one' } } })
      expect(result).to.deep.equal({ 'value.parentField.children1': 'one' })
    })

    it('converts the "beginsWith" operator', () => {
      const result = queryRecordFor({ field: { beginsWith: 'one' } })
      expect(result).to.deep.equal({ 'value.field': { $regex: new RegExp('^one') } })
    })

    it('Use AND as the default WHERE operator', () => {
      const result = queryRecordFor({ field: { contains: 'one' }, field2: { contains: 'two' } })
      expect(result).to.deep.equal({
        'value.field': { $regex: new RegExp('one') },
        'value.field2': { $regex: new RegExp('two') },
      })
    })

    it('converts the "isDefined" operator', () => {
      const result = queryRecordFor({ field: { isDefined: true } })
      expect(result).to.deep.equal({ 'value.field': { $exists: true } })
    })

    it('converts the "isDefined" operator on nested fields', () => {
      const result = queryRecordFor({ field: { otherField: { isDefined: true } } })
      expect(result).to.deep.equal({ 'value.field.otherField': { $exists: true } })
    })

    it('converts the "isDefined" operator for complex filters', () => {
      const result = queryRecordFor({
        and: [
          {
            id: { eq: '3' },
          },
          {
            mainItem: {
              sku: {
                eq: 'test',
              },
            },
          },
          {
            or: [
              {
                days: { isDefined: true },
              },
              {
                items: { includes: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
              },
            ],
          },
          { mainItem: { sku: { eq: null } } },
          { mainItem: { price: { cents: { ne: null } } } },
        ],
      })
      expect(result).to.deep.equal({
        $and: [
          { 'value.id': '3' },
          { 'value.mainItem.sku': 'test' },
          {
            $or: [
              { 'value.days': { $exists: true } },
              {
                'value.items': { $elemMatch: { sku: 'test', price: { cents: 1000, currency: 'EUR' } } },
              },
            ],
          },
          { 'value.mainItem.sku': null },
          { 'value.mainItem.price.cents': { $ne: null } },
        ],
      })
    })
  })
})
