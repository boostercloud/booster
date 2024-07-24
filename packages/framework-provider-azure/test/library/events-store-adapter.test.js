"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const EventsStoreAdapter = require("../../src/library/events-store-adapter");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const cosmos_1 = require("@azure/cosmos");
const constants_1 = require("../../src/constants");
const partition_keys_1 = require("../../src/library/partition-keys");
const event_helper_1 = require("../helpers/event-helper");
describe('Events store adapter', () => {
    let mockConfig;
    let mockEvents;
    let mockCosmosDbClient;
    beforeEach(() => {
        mockCosmosDbClient = (0, sinon_1.createStubInstance)(cosmos_1.CosmosClient, {
            database: (0, sinon_1.stub)().returns({
                container: (0, sinon_1.stub)().returns({
                    items: {
                        batch: sinon_1.fake.resolves({ code: 200 }),
                    },
                }),
            }),
        });
        mockConfig = new framework_types_1.BoosterConfig('test');
        mockEvents = (0, event_helper_1.createMockEventEnvelopes)(2);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('The "storeEvents" method', () => {
        it('Publishes the eventEnvelopes passed via parameter', async () => {
            await EventsStoreAdapter.storeEvents(mockCosmosDbClient, [mockEvents[0]], mockConfig);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore);
            (0, expect_1.expect)(mockCosmosDbClient
                .database(mockConfig.resourceNames.applicationStack)
                .container(mockConfig.resourceNames.eventsStore).items.batch).to.have.been.calledWithExactly([
                {
                    operationType: 'Create',
                    resourceBody: {
                        ...mockEvents[0],
                        [constants_1.eventsStoreAttributes.partitionKey]: (0, partition_keys_1.partitionKeyForEvent)(mockEvents[0].entityTypeName, mockEvents[0].entityID),
                        [constants_1.eventsStoreAttributes.sortKey]: sinon_1.match.defined,
                    },
                },
            ], (0, partition_keys_1.partitionKeyForEvent)(mockEvents[0].entityTypeName, mockEvents[0].entityID));
        });
    });
});
