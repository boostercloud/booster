"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const sinon_1 = require("sinon");
const src_1 = require("../src");
const expect_1 = require("./expect");
describe('the `Searcher` class', () => {
    class SomeModel {
        constructor(someField) {
            this.someField = someField;
        }
    }
    const searcherFunction = sinon_1.fake.resolves([{ hello: 'world' }]);
    const finderByKeyFunction = sinon_1.fake.resolves({ hello: 'world' });
    let searcher;
    beforeEach(() => {
        searcher = new src_1.Searcher(SomeModel, searcherFunction, finderByKeyFunction);
    });
    context('with a valid searcher class', () => {
        describe('the constructor', () => {
            it('creates a valid searcher instance', () => {
                // The searcher instance won't respond with the right class
                // when using a conventional typeOf call because the Searcher class
                // is a generic. The instance keeps a reference to the
                // constructor that we can use though.
                (0, expect_1.expect)(searcher.constructor.name).to.be.equal('Searcher');
            });
        });
        describe('the method `filter`', () => {
            it('adds an array of filters to the searcher and returns the searcher', () => {
                const fakeFilters = { someField: { gt: '200' } };
                const newSearcher = searcher.filter(fakeFilters);
                (0, expect_1.expect)(newSearcher).to.be.deep.equal(searcher);
                (0, expect_1.expect)(newSearcher).to.have.deep.property('filters', fakeFilters);
            });
        });
        describe('the method `limit`', () => {
            it('sets a limit in the searcher and returns the searcher', () => {
                const limit = 42;
                const newSearcher = searcher.limit(limit);
                (0, expect_1.expect)(newSearcher).to.be.deep.equal(searcher);
                (0, expect_1.expect)(newSearcher).to.have.property('_limit', limit);
            });
        });
        describe('the method `afterCursor`', () => {
            it('sets the afterCursor in the searcher and returns the searcher', () => {
                const afterCursor = 42;
                const newSearcher = searcher.afterCursor(afterCursor);
                (0, expect_1.expect)(newSearcher).to.be.deep.equal(searcher);
                (0, expect_1.expect)(newSearcher).to.have.property('_afterCursor', afterCursor);
            });
        });
        describe('the method `paginatedVersion`', () => {
            it('sets the paginatedVersion in the searcher and returns the searcher', () => {
                // Check that the default is false
                (0, expect_1.expect)(searcher).to.have.property('_paginatedVersion', false);
                const paginatedVersion = true;
                const newSearcher = searcher.paginatedVersion(paginatedVersion);
                (0, expect_1.expect)(newSearcher).to.be.deep.equal(searcher);
                (0, expect_1.expect)(newSearcher).to.have.property('_paginatedVersion', paginatedVersion);
            });
        });
        describe('the method `findById`', () => {
            it('calls to the `finderByKeyFunction` with the right parameters', async () => {
                // With a unique primary key
                await searcher.findById('42');
                (0, expect_1.expect)(finderByKeyFunction).to.have.been.calledWith(SomeModel, '42');
                // With a compound primary key (sequenced read models)
                const sequenceKey = { name: 'timestamp', value: '1' };
                await searcher.findById('43', sequenceKey);
                (0, expect_1.expect)(finderByKeyFunction).to.have.been.calledWith(SomeModel, '43', sequenceKey);
            });
        });
        describe('the method `searchOne`', () => {
            it("calls the `searcherFunction` discarding searcher's limit and pagination settings", async () => {
                const filters = { someField: { gt: '200' } };
                const result = await searcher.filter(filters).afterCursor('30').limit(50).paginatedVersion(true).searchOne();
                (0, expect_1.expect)(searcherFunction).to.have.been.calledWith(SomeModel, filters, {}, 1, '30', false);
                (0, expect_1.expect)(result).not.to.be.an('Array');
            });
        });
        describe('the method `search`', () => {
            it('calls the `searcherFunction` forwarding the configured parameters', async () => {
                const filters = { someField: { gt: '200' }, field: { otherField: { isDefined: true } } };
                await searcher.filter(filters).afterCursor('30').limit(50).paginatedVersion(true).search();
                (0, expect_1.expect)(searcherFunction).to.have.been.calledWith(SomeModel, filters, {}, 50, '30', true);
            });
        });
    });
});
