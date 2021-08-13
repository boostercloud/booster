import { fake } from 'sinon'
import { Searcher } from '../src'
import { expect } from './expect'

describe('the `Searcher` class', () => {
  class SomeModel {}

  const searcherFunction = fake()
  const finderByKeyFunction = fake()

  context('with a valid searcher class', () => {
    const searcher = new Searcher(SomeModel, searcherFunction, finderByKeyFunction)

    describe('the constructor', () => {
      it('creates a valid searcher instance', () => {
        expect(searcher).to.be.a('Searcher')
      })
    })

    describe('the method `filter`', () => {
      it('...') // TODO
    })

    describe('the method `limit`', () => {
      it('...') // TODO
    })

    describe('the method `afterCursor`', () => {
      it('...') // TODO
    })

    describe('the method `paginatedVersion`', () => {
      it('...') // TODO
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
