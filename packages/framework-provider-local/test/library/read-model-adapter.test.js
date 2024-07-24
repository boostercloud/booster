"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/ban-ts-comment */
const sinon_1 = require("sinon");
const src_1 = require("../../src");
const framework_types_1 = require("@boostercloud/framework-types");
const expect_1 = require("../expect");
const faker_1 = require("faker");
const read_model_helper_1 = require("../helpers/read-model-helper");
const read_model_adapter_1 = require("../../src/library/read-model-adapter");
const mocha_1 = require("mocha");
async function fetchMock(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID) {
    // @ts-ignore
    return await (0, read_model_adapter_1.fetchReadModel)(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID);
}
async function storeMock(mockReadModelRegistry, mockConfig, mockReadModel) {
    const mockUserApp = {};
    const graphQLService = new src_1.GraphQLService(mockUserApp);
    (0, sinon_1.stub)(graphQLService, 'handleNotificationSubscription');
    await (0, read_model_adapter_1.storeReadModel)(graphQLService, 
    // @ts-ignore
    mockReadModelRegistry, mockConfig, mockReadModel.typeName, mockReadModel.value, 1);
}
async function searchMock(mockReadModelRegistry, mockConfig, mockReadModel, filters, sortBy, limit, afterCursor) {
    // @ts-ignore
    await (0, read_model_adapter_1.searchReadModel)(mockReadModelRegistry, mockConfig, mockReadModel.typeName, filters, sortBy, limit, afterCursor);
}
(0, mocha_1.describe)('read-models-adapter', () => {
    let mockConfig;
    let mockReadModel;
    let loggerDebugStub;
    let storeStub;
    let queryStub;
    let deleteStub;
    let mockReadModelRegistry;
    beforeEach(() => {
        mockConfig = new framework_types_1.BoosterConfig('test');
        mockConfig.appName = 'nuke-button';
        mockConfig.enableSubscriptions = true;
        loggerDebugStub = (0, sinon_1.stub)();
        storeStub = (0, sinon_1.stub)();
        queryStub = (0, sinon_1.stub)();
        deleteStub = (0, sinon_1.stub)();
        mockConfig.logger = {
            info: (0, sinon_1.fake)(),
            warn: (0, sinon_1.fake)(),
            error: (0, sinon_1.fake)(),
            debug: loggerDebugStub,
        };
        mockReadModelRegistry = (0, sinon_1.createStubInstance)(src_1.ReadModelRegistry);
        mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (0, sinon_1.replace)(mockReadModelRegistry, 'store', storeStub);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (0, sinon_1.replace)(mockReadModelRegistry, 'query', queryStub);
        (0, sinon_1.replace)(mockReadModelRegistry, 'deleteById', deleteStub);
    });
    (0, mocha_1.describe)('rawReadModelEventsToEnvelopes', () => {
        it('should return an empty array of envelopes', async () => {
            const results = await (0, read_model_adapter_1.rawReadModelEventsToEnvelopes)(mockConfig, []);
            const expected = [];
            (0, expect_1.expect)(results).to.deep.equal(expected);
        });
        it('should return an array of envelopes', async () => {
            const value1 = (0, read_model_helper_1.createMockReadModelEnvelope)();
            const value2 = (0, read_model_helper_1.createMockReadModelEnvelope)();
            const rawEvents = [value1, value2];
            const results = await (0, read_model_adapter_1.rawReadModelEventsToEnvelopes)(mockConfig, rawEvents);
            const expected = [value1, value2];
            (0, expect_1.expect)(results).to.deep.equal(expected);
        });
    });
    (0, mocha_1.describe)('fetchReadModel', () => {
        let mockReadModelTypeName;
        let mockReadModelID;
        beforeEach(() => {
            mockReadModelTypeName = faker_1.random.alphaNumeric(10);
            mockReadModelID = faker_1.random.uuid();
        });
        it('should call read model registry query and return a value', async () => {
            var _a, _b;
            queryStub.resolves([mockReadModel]);
            const result = (await fetchMock(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID))[0];
            (0, expect_1.expect)(queryStub).to.have.been.calledOnceWithExactly({
                'value.id': mockReadModelID,
                typeName: mockReadModelTypeName,
            });
            (0, expect_1.expect)(result).to.deep.equal(mockReadModel.value);
            (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.not.be.calledWith('[Booster]|read-model-adapter#fetchReadModel: ', `Read model ${mockReadModelTypeName} with ID ${mockReadModelID} not found`);
            (0, expect_1.expect)((_b = mockConfig.logger) === null || _b === void 0 ? void 0 : _b.debug).to.be.calledWith('[Booster]|read-model-adapter#fetchReadModel: ', `Loaded read model ${mockReadModelTypeName} with ID ${mockReadModelID} with result:`);
        });
        it('should call read model registry query and no results', async () => {
            var _a, _b;
            queryStub.resolves([]);
            const result = (await fetchMock(mockReadModelRegistry, mockConfig, mockReadModelTypeName, mockReadModelID))[0];
            (0, expect_1.expect)(queryStub).to.have.been.calledOnceWithExactly({
                'value.id': mockReadModelID,
                typeName: mockReadModelTypeName,
            });
            (0, expect_1.expect)(result).to.be.undefined;
            (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.be.calledWith('[Booster]|read-model-adapter#fetchReadModel: ', `Read model ${mockReadModelTypeName} with ID ${mockReadModelID} not found`);
            (0, expect_1.expect)((_b = mockConfig.logger) === null || _b === void 0 ? void 0 : _b.debug).to.not.be.calledWith(`[ReadModelAdapter#fetchReadModel] Loaded read model ${mockReadModelTypeName} with ID ${mockReadModelID} with result:`);
        });
    });
    (0, mocha_1.describe)('storeReadModel', () => {
        let mockReadModel;
        beforeEach(async () => {
            mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
            await storeMock(mockReadModelRegistry, mockConfig, mockReadModel);
        });
        it('should call read model registry store', () => {
            (0, expect_1.expect)(storeStub).to.have.been.calledWithExactly(mockReadModel, 1);
        });
        it('should log the right debug message', () => {
            var _a;
            (0, expect_1.expect)((_a = mockConfig.logger) === null || _a === void 0 ? void 0 : _a.debug).to.have.been.calledWithExactly('[Booster]|read-model-adapter#storeReadModel: ', 'Read model stored');
        });
    });
    (0, mocha_1.describe)('searchReadModel', () => {
        it('empty query should call read model registry store', async () => {
            const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
            await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {});
            (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                typeName: mockReadModel.typeName,
            }, undefined, 0, undefined, undefined);
        });
        (0, mocha_1.describe)('query by one field', () => {
            it('eq query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { eq: 1 },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': 1 }, undefined, 0, undefined, undefined);
            });
            it('ne query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { ne: 1 },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $ne: 1 } }, undefined, 0, undefined, undefined);
            });
            it('lt query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { lt: 1 },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $lt: 1 } }, undefined, 0, undefined, undefined);
            });
            it('gt query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { gt: 1 },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $gt: 1 } }, undefined, 0, undefined, undefined);
            });
            it('lte query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { lte: 1 },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $lte: 1 } }, undefined, 0, undefined, undefined);
            });
            it('gte query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { gte: 1 },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName, 'value.foo': { $gte: 1 } }, undefined, 0, undefined, undefined);
            });
            it('gte query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { in: [1, 2, 3] },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    'value.foo': { $in: [1, 2, 3] },
                }, undefined, 0, undefined, undefined);
            });
            it('contains query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { contains: 'bar' },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    'value.foo': { $regex: new RegExp('bar') },
                }, undefined, 0, undefined, undefined);
            });
            it('includes query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { includes: 'bar' },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    'value.foo': { $regex: new RegExp('bar') },
                }, undefined, 0, undefined, undefined);
            });
            it('includes object query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { includes: { bar: 'baz' } },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    'value.foo': { $elemMatch: { bar: 'baz' } },
                }, undefined, 0, undefined, undefined);
            });
            it('beginsWith query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: { beginsWith: 'bar' },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    'value.foo': { $regex: new RegExp('^bar') },
                }, undefined, 0, undefined, undefined);
            });
            it('NOT beginsWith query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    not: { foo: { beginsWith: 'bar' } },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    $not: { 'value.foo': { $regex: new RegExp('^bar') } },
                }, undefined, 0, undefined, undefined);
            });
        });
        (0, mocha_1.describe)('multiple queries', () => {
            it('only fields query should use AND and call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    foo: {
                        subFooField: { eq: 'subFooField' },
                    },
                    bar: {
                        subBarField: { eq: 'subBarField' },
                    },
                    other: {
                        subOtherField: { ne: true },
                    },
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    'value.foo.subFooField': 'subFooField',
                    'value.bar.subBarField': 'subBarField',
                    'value.other.subOtherField': { $ne: true },
                    typeName: mockReadModel.typeName,
                }, undefined, 0, undefined, undefined);
            });
            it('gt lt AND query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    and: [{ foo: { gt: 1 } }, { foo: { lt: 10 } }],
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    $and: [{ 'value.foo': { $gt: 1 } }, { 'value.foo': { $lt: 10 } }],
                }, undefined, 0, undefined, undefined);
            });
            it('gte lte AND query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    and: [{ foo: { gte: 1 } }, { foo: { lte: 10 } }],
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    $and: [{ 'value.foo': { $gte: 1 } }, { 'value.foo': { $lte: 10 } }],
                }, undefined, 0, undefined, undefined);
            });
            it('OR query should call read model registry store with the appropriate operation converted', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {
                    or: [{ foo: { eq: 1 } }, { bar: { lt: 10 } }],
                });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({
                    typeName: mockReadModel.typeName,
                    $or: [{ 'value.foo': 1 }, { 'value.bar': { $lt: 10 } }],
                }, undefined, 0, undefined, undefined);
            });
        });
        (0, mocha_1.describe)('Sort fields', () => {
            it('query should call read model registry store with sort fields, limits and skip', async () => {
                const mockReadModel = (0, read_model_helper_1.createMockReadModelEnvelope)();
                await searchMock(mockReadModelRegistry, mockConfig, mockReadModel, {}, [
                    {
                        field: 'ID',
                        order: 'DESC',
                    },
                    {
                        field: 'anotherField',
                        order: 'ASC',
                    },
                ], 3, { id: '5' });
                (0, expect_1.expect)(queryStub).to.have.been.calledWithExactly({ typeName: mockReadModel.typeName }, [
                    { field: 'ID', order: 'DESC' },
                    {
                        field: 'anotherField',
                        order: 'ASC',
                    },
                ], 5, 3, undefined);
            });
        });
    });
    (0, mocha_1.describe)('deleteReadModel', () => {
        it('delete one read model', async () => {
            const expectedId = faker_1.random.uuid();
            const mockReadModelInterface = {
                id: expectedId,
                age: faker_1.random.number(40),
                foo: faker_1.random.word(),
                bar: faker_1.random.float(),
                boosterMetadata: {
                    version: 1,
                    schemaVersion: 1,
                },
            };
            const expectedName = 'readModel';
            await (0, read_model_adapter_1.deleteReadModel)(mockReadModelRegistry, mockConfig, expectedName, mockReadModelInterface);
            (0, expect_1.expect)(deleteStub).to.have.been.calledWithExactly(expectedId, expectedName);
        });
    });
});
