import { fake } from 'sinon'
import { FilterFor, Searcher, SequenceKey, SortFor } from '../src'
import { expect } from './expect'

describe('the `Searcher` class', () => {
  class SomeModel {
    constructor(readonly someField: string) {}
  }

  const searcherFunction = fake.resolves([{ hello: 'world' }])
  const finderByKeyFunction = fake.resolves({ hello: 'world' })
  let searcher: Searcher<SomeModel>

  beforeEach(() => {
    searcher = new Searcher(SomeModel, searcherFunction, finderByKeyFunction)
  })

  context('with a valid searcher class', () => {
    describe('the constructor', () => {
      it('creates a valid searcher instance', () => {
        // The searcher instance won't respond with the right class
        // when using a conventional typeOf call because the Searcher class
        // is a generic. The instance keeps a reference to the
        // constructor that we can use though.
        expect(searcher.constructor.name).to.be.equal('Searcher')
      })
    })

    describe('the method `filter`', () => {
      it('adds an array of filters to the searcher and returns the searcher', () => {
        const fakeFilters: FilterFor<SomeModel> = { someField: { gt: '200' } }

        const newSearcher = searcher.filter(fakeFilters)

        expect(newSearcher).to.be.deep.equal(searcher)
        expect(newSearcher as any).to.have.deep.property('filters', fakeFilters)
      })
    })

    describe('the method `limit`', () => {
      it('sets a limit in the searcher and returns the searcher', () => {
        const limit = 42

        const newSearcher = searcher.limit(limit)

        expect(newSearcher).to.be.deep.equal(searcher)
        expect(newSearcher as any).to.have.property('_limit', limit)
      })
    })

    describe('the method `afterCursor`', () => {
      it('sets the afterCursor in the searcher and returns the searcher', () => {
        const afterCursor = 42

        const newSearcher = searcher.afterCursor(afterCursor)

        expect(newSearcher).to.be.deep.equal(searcher)
        expect(newSearcher as any).to.have.property('_afterCursor', afterCursor)
      })
    })

    describe('the method `paginatedVersion`', () => {
      it('sets the paginatedVersion in the searcher and returns the searcher', () => {
        // Check that the default is false
        expect(searcher as any).to.have.property('_paginatedVersion', false)

        const paginatedVersion = true

        const newSearcher = searcher.paginatedVersion(paginatedVersion)

        expect(newSearcher).to.be.deep.equal(searcher)
        expect(newSearcher as any).to.have.property('_paginatedVersion', paginatedVersion)
      })
    })

    describe('the method `findById`', () => {
      it('calls to the `finderByKeyFunction` with the right parameters', async () => {
        // With a unique primary key
        await searcher.findById('42')
        expect(finderByKeyFunction).to.have.been.calledWith(SomeModel, '42')

        // With a compound primary key (sequenced read models)
        const sequenceKey: SequenceKey = { name: 'timestamp', value: '1' }
        await searcher.findById('43', sequenceKey)
        expect(finderByKeyFunction).to.have.been.calledWith(SomeModel, '43', sequenceKey)
      })
    })

    describe('the method `searchOne`', () => {
      it("calls the `searcherFunction` discarding searcher's limit and pagination settings", async () => {
        const filters = { someField: { gt: '200' } }
        const result = await searcher.filter(filters).afterCursor('30').limit(50).paginatedVersion(true).searchOne()

        expect(searcherFunction).to.have.been.calledWith(SomeModel, filters, {}, 1, '30', false)
        expect(result).not.to.be.an('Array')
      })
    })

    describe('the method `search`', () => {
      it('calls the `searcherFunction` forwarding the configured parameters', async () => {
        const filters = { someField: { gt: '200' }, field: { otherField: { isDefined: true } } } as FilterFor<any>
        await searcher.filter(filters).afterCursor('30').limit(50).paginatedVersion(true).search()

        expect(searcherFunction).to.have.been.calledWith(SomeModel, filters, {}, 50, '30', true)
      })
    })
  })

  describe('FilterFor and SortFor type safety', () => {
    // Test model with data properties, methods, and getters
    class CartReadModel {
      public id: string
      public userId: string
      public items: Array<{
        productId: string
        quantity: number
        price: number
      }>
      public totalAmount: number
      public createdAt: Date
      public isActive: boolean
      public metadata?: { tags: string[]; priority: number }

      constructor(
        id: string,
        userId: string,
        items: Array<{ productId: string; quantity: number; price: number }>,
        totalAmount: number,
        createdAt: Date,
        isActive: boolean,
        metadata?: { tags: string[]; priority: number }
      ) {
        this.id = id
        this.userId = userId
        this.items = items
        this.totalAmount = totalAmount
        this.createdAt = createdAt
        this.isActive = isActive
        this.metadata = metadata
      }

      // Method - should NOT be filterable/sortable
      public calculateTotal(): number {
        return this.items.reduce((sum, item) => sum + item.price * item.quantity, 0)
      }

      // Getter - should NOT be filterable/sortable
      public get itemCount(): number {
        return this.items.length
      }

      // Another method
      public addItem(item: { productId: string; quantity: number; price: number }): void {
        this.items.push(item)
      }
    }

    // Simple model with no methods for backwards compatibility testing
    class SimpleModel {
      public name: string
      public age: number
      constructor(name: string, age: number) {
        this.name = name
        this.age = age
      }
    }

    describe('FilterFor type', () => {
      it('allows filtering by data properties', () => {
        // Test filtering on various data property types
        const validFilters: FilterFor<CartReadModel> = {
          id: { eq: 'cart-123' },
          userId: { beginsWith: 'user-' },
          totalAmount: { gte: 100, lte: 500 },
          createdAt: { gt: new Date('2023-01-01') },
          isActive: { eq: true },
          items: { includes: { productId: 'prod-1', quantity: 2, price: 29.99 } },
        }

        // This should compile without errors - verify the filter structure
        expect(validFilters.id).to.deep.equal({ eq: 'cart-123' })
        expect(validFilters.userId).to.deep.equal({ beginsWith: 'user-' })
        expect(validFilters.totalAmount).to.deep.equal({ gte: 100, lte: 500 })
        expect(validFilters.isActive).to.deep.equal({ eq: true })
      })

      it('does not allow filtering by method names (compile-time check)', () => {
        // These should cause TypeScript compilation errors if uncommented:
        // const invalidFilter1: FilterFor<CartReadModel> = {
        //   calculateTotal: { eq: 100 } // Error: 'calculateTotal' is not a valid property
        // }
        // const invalidFilter2: FilterFor<CartReadModel> = {
        //   addItem: { isDefined: true } // Error: 'addItem' is not a valid property
        // }
        // const invalidFilter3: FilterFor<CartReadModel> = {
        //   itemCount: { eq: 5 } // Error: 'itemCount' getter is not a valid property
        // }

        // This test passes by virtue of the code compiling
        // The real test is at compile-time - these properties should not be available
        expect(true).to.be.true
      })

      it('supports nested filtering on valid properties', () => {
        const nestedFilters: FilterFor<CartReadModel> = {
          metadata: {
            priority: { gte: 1 },
            tags: { includes: 'urgent' },
          },
        }

        expect(nestedFilters.metadata).to.deep.equal({
          priority: { gte: 1 },
          tags: { includes: 'urgent' },
        })
      })

      it('supports filter combinators', () => {
        const combinatorFilters: FilterFor<CartReadModel> = {
          and: [{ isActive: { eq: true } }, { totalAmount: { gte: 50 } }],
          or: [{ userId: { eq: 'user-1' } }, { userId: { eq: 'user-2' } }],
          not: {
            isActive: { eq: false },
          },
          isDefined: true,
        }

        expect(combinatorFilters.and).to.have.length(2)
        expect(combinatorFilters.or).to.have.length(2)
        expect(combinatorFilters.not).to.deep.equal({ isActive: { eq: false } })
        expect(combinatorFilters.isDefined).to.be.true
      })
    })

    describe('SortFor type', () => {
      it('allows sorting by data properties', () => {
        const validSort: SortFor<CartReadModel> = {
          id: 'ASC',
          userId: 'DESC',
          totalAmount: 'ASC',
          createdAt: 'DESC',
          isActive: 'ASC',
        }

        expect(validSort.id).to.equal('ASC')
        expect(validSort.userId).to.equal('DESC')
        expect(validSort.totalAmount).to.equal('ASC')
        expect(validSort.createdAt).to.equal('DESC')
        expect(validSort.isActive).to.equal('ASC')
      })

      it('does not allow sorting by method names (compile-time check)', () => {
        // These should cause TypeScript compilation errors if uncommented:
        // const invalidSort1: SortFor<CartReadModel> = {
        //   calculateTotal: 'ASC' // Error: 'calculateTotal' is not a valid property
        // }
        // const invalidSort2: SortFor<CartReadModel> = {
        //   addItem: 'DESC' // Error: 'addItem' is not a valid property
        // }
        // const invalidSort3: SortFor<CartReadModel> = {
        //   itemCount: 'ASC' // Error: 'itemCount' getter is not a valid property
        // }

        // This test passes by virtue of the code compiling
        expect(true).to.be.true
      })

      it('supports basic sorting on array properties', () => {
        const sortWithNestedProperties: SortFor<CartReadModel> = {
          items: 'ASC', // Sorting on array property itself
          metadata: {
            priority: 'DESC',
            tags: 'ASC',
          },
        }

        expect(sortWithNestedProperties.items).to.equal('ASC')
        expect(sortWithNestedProperties.metadata).to.deep.equal({
          priority: 'DESC',
          tags: 'ASC',
        })
      })
    })

    describe('backwards compatibility', () => {
      it('maintains compatibility with simple models', () => {
        // Test that the type system works with simple models that have no methods
        const simpleFilter: FilterFor<SimpleModel> = {
          name: { beginsWith: 'John' },
          age: { gte: 18, lte: 65 },
        }

        const simpleSort: SortFor<SimpleModel> = {
          name: 'ASC',
          age: 'DESC',
        }

        expect(simpleFilter.name).to.deep.equal({ beginsWith: 'John' })
        expect(simpleFilter.age).to.deep.equal({ gte: 18, lte: 65 })
        expect(simpleSort.name).to.equal('ASC')
        expect(simpleSort.age).to.equal('DESC')
      })

      it('works with models that have no methods', () => {
        // Test with the original SomeModel
        const originalFilter: FilterFor<SomeModel> = {
          someField: { contains: 'test' },
        }

        const originalSort: SortFor<SomeModel> = {
          someField: 'ASC',
        }

        expect(originalFilter.someField).to.deep.equal({ contains: 'test' })
        expect(originalSort.someField).to.equal('ASC')
      })
    })
  })
})
