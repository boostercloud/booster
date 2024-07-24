"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const read_model_adapter_1 = require("../../src/library/read-model-adapter");
const framework_types_1 = require("@boostercloud/framework-types");
const cosmos_1 = require("@azure/cosmos");
const faker_1 = require("faker");
const read_model_helper_1 = require("../helpers/read-model-helper");
describe('Read Model adapter', () => {
    let mockConfig;
    let mockReadModel;
    let mockReadModelName;
    let mockReadModelId;
    let mockCosmosDbClient;
    beforeEach(() => {
        mockCosmosDbClient = (0, sinon_1.createStubInstance)(cosmos_1.CosmosClient, {
            database: (0, sinon_1.stub)().returns({
                container: (0, sinon_1.stub)().returns({
                    items: {
                        query: (0, sinon_1.stub)().returns({
                            fetchAll: sinon_1.fake.resolves({ resources: [] }),
                        }),
                        upsert: (0, sinon_1.stub)().returns(sinon_1.fake.resolves({})),
                        create: (0, sinon_1.stub)().returns(sinon_1.fake.resolves({})),
                    },
                    item: (0, sinon_1.stub)().returns({
                        read: (0, sinon_1.stub)().returns(sinon_1.fake.resolves({})),
                    }),
                }),
            }),
        });
        mockConfig = new framework_types_1.BoosterConfig('test');
        mockReadModelName = faker_1.random.word();
        mockReadModelId = faker_1.random.uuid();
        mockReadModel = (0, read_model_helper_1.createMockReadModel)();
    });
    describe('The "fetchReadModel" method', () => {
        it('Responds with a read model when it exists', async () => {
            const result = (await (0, read_model_adapter_1.fetchReadModel)(mockCosmosDbClient, mockConfig, mockReadModelName, mockReadModelId))[0];
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient
                .database(mockConfig.resourceNames.applicationStack)
                .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).item).to.have.been.calledWithExactly(mockReadModelId, mockReadModelId);
            (0, expect_1.expect)(result).not.to.be.null;
        });
    });
    describe('The "storeReadModel" method', () => {
        it('Saves a read model', async () => {
            const something = await (0, read_model_adapter_1.storeReadModel)(mockCosmosDbClient, mockConfig, mockReadModelName, mockReadModel);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`);
            (0, expect_1.expect)(mockCosmosDbClient
                .database(mockConfig.resourceNames.applicationStack)
                .container(`${mockConfig.resourceNames.applicationStack}-${mockReadModelName}`).items.create).to.have.been.calledWithExactly((0, sinon_1.match)(mockReadModel));
            (0, expect_1.expect)(something).not.to.be.null;
        });
    });
});
