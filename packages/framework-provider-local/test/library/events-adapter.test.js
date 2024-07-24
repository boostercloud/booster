"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const sinon_1 = require("sinon");
const services_1 = require("../../src/services");
const events_adapter_1 = require("../../src/library/events-adapter");
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("../expect");
const event_helper_1 = require("../helpers/event-helper");
const faker_1 = require("faker");
describe('events-adapter', () => {
    let mockConfig;
    let mockEventEnvelop;
    let mockSnapshot;
    let loggerDebugStub;
    let storeStub;
    let queryStub;
    let queryLatestStub;
    let boosterEventDispatcherStub;
    let mockUserApp;
    let mockEventRegistry;
    beforeEach(() => {
        mockConfig = new framework_types_1.BoosterConfig('test');
        mockConfig.appName = 'nuke-button';
        mockEventEnvelop = (0, event_helper_1.createMockNonPersistedEventEnvelop)();
        mockSnapshot = (0, event_helper_1.createMockEntitySnapshotEnvelope)();
        loggerDebugStub = (0, sinon_1.stub)();
        storeStub = (0, sinon_1.stub)();
        boosterEventDispatcherStub = (0, sinon_1.stub)();
        queryStub = (0, sinon_1.stub)();
        queryLatestStub = (0, sinon_1.stub)();
        mockConfig.logger = {
            info: (0, sinon_1.fake)(),
            warn: (0, sinon_1.fake)(),
            error: (0, sinon_1.fake)(),
            debug: loggerDebugStub,
        };
        mockUserApp = {
            boosterEventDispatcher: boosterEventDispatcherStub,
        };
        mockEventRegistry = (0, sinon_1.createStubInstance)(services_1.EventRegistry);
        (0, sinon_1.replace)(mockEventRegistry, 'store', storeStub);
        (0, sinon_1.replace)(mockEventRegistry, 'query', queryStub);
        (0, sinon_1.replace)(mockEventRegistry, 'queryLatestSnapshot', queryLatestStub);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('rawEventsToEnvelopes', () => {
        it('should return an empty array of envelopes', async () => {
            const results = (0, events_adapter_1.rawEventsToEnvelopes)([]);
            const expected = [];
            (0, expect_1.expect)(results).to.deep.equal(expected);
        });
        it('should return an array of envelopes', async () => {
            const event1 = (0, event_helper_1.createMockNonPersistedEventEnvelop)();
            const event2 = (0, event_helper_1.createMockNonPersistedEventEnvelop)();
            const rawEvents = [event1, event2];
            const results = (0, events_adapter_1.rawEventsToEnvelopes)(rawEvents);
            const expected = [event1, event2];
            (0, expect_1.expect)(results).to.deep.equal(expected);
        });
    });
    describe('readEntityEventsSince', () => {
        let mockEntityTypeName;
        let mockEntityID;
        beforeEach(() => {
            queryStub.resolves([mockEventEnvelop]);
            mockEntityTypeName = faker_1.random.alphaNumeric(10);
            mockEntityID = faker_1.random.uuid();
        });
        it('should return expected result', async () => {
            var _a;
            const result = await (0, events_adapter_1.readEntityEventsSince)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
            const expectedLogMessage = `Loaded events for entity ${mockEntityTypeName} with ID ${mockEntityID} with result:`;
            (0, expect_1.expect)(result).to.be.deep.equal([mockEventEnvelop]);
            (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.be.calledWith('[Booster]|events-adapter#readEntityEventsSince: ', expectedLogMessage, [mockEventEnvelop]);
        });
        context('date provided', () => {
            let dateStr;
            beforeEach(async () => {
                dateStr = faker_1.date.recent().toISOString();
                await (0, events_adapter_1.readEntityEventsSince)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID, dateStr);
            });
            it('should call event registry query with expected input', async () => {
                (0, expect_1.expect)(queryStub).to.have.been.calledOnceWithExactly({
                    createdAt: {
                        $gt: dateStr,
                    },
                    kind: 'event',
                    entityID: mockEntityID,
                    entityTypeName: mockEntityTypeName,
                });
            });
            it('should call logger with message', async () => {
                var _a;
                const expectedLogMessage = `Loaded events for entity ${mockEntityTypeName} with ID ${mockEntityID} with result:`;
                (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.be.calledWith('[Booster]|events-adapter#readEntityEventsSince: ', expectedLogMessage, [mockEventEnvelop]);
            });
        });
        context('date not provided', () => {
            beforeEach(async () => {
                await (0, events_adapter_1.readEntityEventsSince)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
            });
            it('should call event registry query with expected input', () => {
                (0, expect_1.expect)(queryStub).to.have.been.calledOnceWithExactly({
                    createdAt: {
                        $gt: new Date(0).toISOString(),
                    },
                    kind: 'event',
                    entityID: mockEntityID,
                    entityTypeName: mockEntityTypeName,
                });
            });
            it('should call logger with message', async () => {
                var _a;
                const expectedLogMessage = `Loaded events for entity ${mockEntityTypeName} with ID ${mockEntityID} with result:`;
                (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.be.calledWith('[Booster]|events-adapter#readEntityEventsSince: ', expectedLogMessage, [mockEventEnvelop]);
            });
        });
    });
    describe('readEntityLatestSnapshot', () => {
        let mockEntityTypeName;
        let mockEntityID;
        beforeEach(() => {
            queryLatestStub.resolves(mockSnapshot);
            mockEntityTypeName = faker_1.random.alphaNumeric(10);
            mockEntityID = faker_1.random.uuid();
        });
        it('should call event registry queryLatest', async () => {
            await (0, events_adapter_1.readEntityLatestSnapshot)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
            (0, expect_1.expect)(queryLatestStub).to.have.been.calledOnceWithExactly({
                entityID: mockEntityID,
                entityTypeName: mockEntityTypeName,
                kind: 'snapshot',
            });
        });
        context('with snapshot', () => {
            beforeEach(() => {
                queryLatestStub.resolves(mockSnapshot);
            });
            it('should log expected message', async () => {
                await (0, events_adapter_1.readEntityLatestSnapshot)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
                (0, expect_1.expect)(loggerDebugStub).to.have.been.calledOnceWithExactly('[Booster]|events-adapter#readEntityLatestSnapshot: ', `Snapshot found for entity ${mockEntityTypeName} with ID ${mockEntityID}:`, mockSnapshot);
            });
            it('should return expected result', async () => {
                const result = await (0, events_adapter_1.readEntityLatestSnapshot)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
                (0, expect_1.expect)(result).to.be.deep.equal(mockSnapshot);
            });
        });
        context('without snapshot', () => {
            beforeEach(async () => {
                queryLatestStub.resolves(null);
            });
            it('should log expected message', async () => {
                await (0, events_adapter_1.readEntityLatestSnapshot)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
                (0, expect_1.expect)(loggerDebugStub).to.have.been.calledOnceWithExactly('[Booster]|events-adapter#readEntityLatestSnapshot: ', `No snapshot found for entity ${mockEntityTypeName} with ID ${mockEntityID}.`);
            });
            it('should return null', async () => {
                const result = await (0, events_adapter_1.readEntityLatestSnapshot)(mockEventRegistry, mockConfig, mockEntityTypeName, mockEntityID);
                (0, expect_1.expect)(result).to.be.undefined;
            });
        });
    });
    describe('storeEvents', () => {
        context('no event envelopes', () => {
            beforeEach(async () => {
                await (0, events_adapter_1.storeEvents)(mockUserApp, mockEventRegistry, [], mockConfig);
            });
            it('should not call event registry store', () => {
                (0, expect_1.expect)(storeStub).not.to.have.been.called;
            });
            it('should call userApp boosterEventDispatcher', () => {
                (0, expect_1.expect)(boosterEventDispatcherStub).to.have.been.calledOnceWithExactly([]);
            });
        });
        context('with event envelopes', () => {
            it('should call event registry store', async () => {
                const mockEventEnvelop = (0, event_helper_1.createMockNonPersistedEventEnvelop)();
                // The `createdAt` will be set in the `persistEvent` method
                (0, sinon_1.replace)(Date.prototype, 'toISOString', () => 'a magical time');
                await (0, events_adapter_1.storeEvents)(mockUserApp, mockEventRegistry, [mockEventEnvelop], mockConfig);
                (0, expect_1.expect)(storeStub).to.have.been.calledWithExactly({
                    ...mockEventEnvelop,
                    createdAt: 'a magical time',
                });
            });
            it('should call userApp boosterEventDispatcher', async () => {
                const mockEventEnvelop = (0, event_helper_1.createMockNonPersistedEventEnvelop)();
                // The `createdAt` will be set in the `persistEvent` method
                (0, sinon_1.replace)(Date.prototype, 'toISOString', () => 'a magical time');
                await (0, events_adapter_1.storeEvents)(mockUserApp, mockEventRegistry, [mockEventEnvelop], mockConfig);
                (0, expect_1.expect)(boosterEventDispatcherStub).to.have.been.calledOnceWithExactly([
                    { ...mockEventEnvelop, createdAt: 'a magical time' },
                ]);
            });
        });
    });
});
