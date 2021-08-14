import { fake } from 'sinon'
import { FilterFor, Searcher, SequenceKey } from '../src'
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
        const fakeFilters: FilterFor<SomeModel> = { someField: { gt: '200' }}

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
        expect(finderByKeyFunction).to.have.been.calledWith('SomeModel', '42')

        // With a compound primary key (sequenced read models)
        const sequenceKey: SequenceKey = { name: 'timestamp', value: '1' }
        await searcher.findById('43', sequenceKey)
        expect(finderByKeyFunction).to.have.been.calledWith('SomeModel', '43', sequenceKey)
      })
    })

    describe('the method `searchOne`', () => {
      it("calls the `searcherFunction` discarding searcher's limit and pagination settings", async () => {
        const filters = { someField: { gt: '200' } }
        const result = await searcher
          .filter(filters)
          .afterCursor('30')
          .limit(50)
          .paginatedVersion(true)
          .searchOne()

        expect(searcherFunction).to.have.been.calledWithMatch(
          'SomeModel',
          filters,
          1,
          '30',
          false
        )
        expect(result).not.to.be.an('Array')
      })
    })

    describe('the method `search`', () => {
      it('calls the `searcherFunction` forwarding the configured parameters', async () => {
        const filters = { someField: { gt: '200' } }
        await searcher
          .filter(filters)
          .afterCursor('30')
          .limit(50)
          .paginatedVersion(true)
          .search()

        expect(searcherFunction).to.have.been.calledWithMatch(
          'SomeModel',
          filters,
          50,
          '30',
          true
        )
      })
    })
  })
})
