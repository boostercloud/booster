import { fake } from 'sinon'
import { Searcher } from '../src'
import { expect } from './expect'

describe('the `Searcher` class', () => {
  class SomeModel {}

  const searcherFunction = fake()
  const finderByKeyFunction = fake()
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
        const fakeFilters = [{name: 'a'},{name: 'b'}]

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
      it('...') // TODO
    })

    describe('the method `searchOne`', () => {
      it('...') // TODO
    })

    describe('the method `search`', () => {
      it('...') // TODO
    })
  })
})
