"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const events_search_adapter_1 = require("../../src/library/events-search-adapter");
const expect_1 = require("../expect");
const web_socket_registry_1 = require("../../src/services/web-socket-registry");
describe('The "searchEntitiesIDs" method', () => {
    let mockConfig;
    let queryStub;
    let mockEventRegistry;
    beforeEach(() => {
        mockConfig = new framework_types_1.BoosterConfig('test');
        queryStub = (0, sinon_1.stub)();
        mockEventRegistry = (0, sinon_1.createStubInstance)(web_socket_registry_1.WebSocketRegistry);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (0, sinon_1.replace)(mockEventRegistry, 'query', queryStub);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('Generate query for entityTypeName, limit and afterCursor has all fields', async () => {
        const limit = 1;
        const afterCursor = { id: '1' };
        const entityTypeName = 'entity';
        await (0, events_search_adapter_1.searchEntitiesIds)(mockEventRegistry, mockConfig, limit, afterCursor, entityTypeName);
        (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ kind: 'event', entityTypeName: 'entity' }, -1, undefined, {
            entityID: 1,
        });
    });
    it('Generate query for entityTypeName, limit has all fields', async () => {
        const limit = 1;
        const entityTypeName = 'entity';
        await (0, events_search_adapter_1.searchEntitiesIds)(mockEventRegistry, mockConfig, limit, undefined, entityTypeName);
        (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ kind: 'event', entityTypeName: 'entity' }, -1, undefined, {
            entityID: 1,
        });
    });
});
