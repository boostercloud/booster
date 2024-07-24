"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const cosmos_1 = require("@azure/cosmos");
const framework_types_1 = require("@boostercloud/framework-types");
const events_searcher_adapter_1 = require("../../src/library/events-searcher-adapter");
const searchModule = require("../../src/helpers/query-helper");
describe('Events Searcher adapter', () => {
    describe('The "searchEvents" method', () => {
        let mockConfig;
        let mockCosmosDbClient;
        beforeEach(() => {
            mockConfig = new framework_types_1.BoosterConfig('test');
            mockCosmosDbClient = (0, sinon_1.createStubInstance)(cosmos_1.CosmosClient, {
                database: (0, sinon_1.stub)().returns({
                    container: (0, sinon_1.stub)().returns({
                        items: {
                            query: (0, sinon_1.stub)().returns({
                                fetchAll: sinon_1.fake.resolves({ resources: [] }),
                            }),
                        },
                    }),
                }),
            });
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('Generate filters for entity, entityId and time when EventSearchParameters has all fields', async () => {
            const filters = {
                from: 'from',
                to: 'to',
                entity: 'entity',
                entityID: 'entityID',
                type: 'type',
            };
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            await (0, events_searcher_adapter_1.searchEvents)(mockCosmosDbClient, mockConfig, filters);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, {
                entityTypeName_entityID_kind: { eq: 'entity-entityID-event' },
                createdAt: { gte: 'from', lte: 'to' },
                kind: { eq: 'event' },
            }, undefined, undefined, undefined, {
                createdAt: 'DESC',
            });
        });
        it('Generate filters for entity, entityId and time when EventSearchParameters has all fields and limited', async () => {
            const filters = {
                from: 'from',
                to: 'to',
                entity: 'entity',
                entityID: 'entityID',
                type: 'type',
                limit: 3,
            };
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            await (0, events_searcher_adapter_1.searchEvents)(mockCosmosDbClient, mockConfig, filters);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, {
                entityTypeName_entityID_kind: { eq: 'entity-entityID-event' },
                createdAt: { gte: 'from', lte: 'to' },
                kind: { eq: 'event' },
            }, 3, undefined, undefined, {
                createdAt: 'DESC',
            });
        });
        it('Generate filters for entity, entityId when EventSearchParameters has entity and entityID fields', async () => {
            const filters = {
                entity: 'entity',
                entityID: 'entityID',
            };
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            await (0, events_searcher_adapter_1.searchEvents)(mockCosmosDbClient, mockConfig, filters);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, {
                entityTypeName_entityID_kind: { eq: 'entity-entityID-event' },
                kind: { eq: 'event' },
            }, undefined, undefined, undefined, {
                createdAt: 'DESC',
            });
        });
        it('Generate filters for type when EventSearchParameters has type field', async () => {
            const filters = {
                type: 'type',
            };
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            await (0, events_searcher_adapter_1.searchEvents)(mockCosmosDbClient, mockConfig, filters);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, {
                typeName: { eq: 'type' },
                kind: { eq: 'event' },
            }, undefined, undefined, undefined, {
                createdAt: 'DESC',
            });
        });
        it('Generate filters for entity when EventSearchParameters has only entity field', async () => {
            const parameters = {
                entity: 'entity',
            };
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            await (0, events_searcher_adapter_1.searchEvents)(mockCosmosDbClient, mockConfig, parameters);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, {
                entityTypeName: { eq: 'entity' },
                kind: { eq: 'event' },
            }, undefined, undefined, undefined, {
                createdAt: 'DESC',
            });
        });
    });
    describe('The "searchEntitiesIDs" method', () => {
        let mockConfig;
        let mockCosmosDbClient;
        beforeEach(() => {
            mockConfig = new framework_types_1.BoosterConfig('test');
            mockCosmosDbClient = (0, sinon_1.createStubInstance)(cosmos_1.CosmosClient, {
                database: (0, sinon_1.stub)().returns({
                    container: (0, sinon_1.stub)().returns({
                        items: {
                            query: (0, sinon_1.stub)().returns({
                                fetchAll: sinon_1.fake.resolves({ resources: [] }),
                            }),
                        },
                    }),
                }),
            });
        });
        afterEach(() => {
            (0, sinon_1.restore)();
        });
        it('Generate query for entityTypeName, limit and afterCursor has all fields', async () => {
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            const limit = 1;
            const afterCursor = { id: '1' };
            const entityTypeName = 'entity';
            await (0, events_searcher_adapter_1.searchEntitiesIds)(mockCosmosDbClient, mockConfig, limit, afterCursor, entityTypeName);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, { kind: { eq: 'event' }, entityTypeName: { eq: 'entity' } }, 1, { id: '1' }, true, undefined, 'DISTINCT c.entityID');
        });
        it('Generate query for entityTypeName, limit has all fields', async () => {
            const mockSearch = (0, sinon_1.stub)(searchModule, 'search').returns(Promise.resolve([]));
            const eventStoreName = 'new-booster-app-app-events-store';
            const limit = 1;
            const entityTypeName = 'entity';
            await (0, events_searcher_adapter_1.searchEntitiesIds)(mockCosmosDbClient, mockConfig, limit, undefined, entityTypeName);
            (0, expect_1.expect)(mockSearch).to.have.been.calledWithExactly(mockCosmosDbClient, mockConfig, eventStoreName, { kind: { eq: 'event' }, entityTypeName: { eq: 'entity' } }, 1, undefined, true, undefined, 'DISTINCT c.entityID');
        });
    });
});
