"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const EventsAdapter = require("../../src/library/events-adapter");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const cosmos_1 = require("@azure/cosmos");
const constants_1 = require("../../src/constants");
const partition_keys_1 = require("../../src/library/partition-keys");
const faker_1 = require("faker");
const event_helper_1 = require("../helpers/event-helper");
describe('Events adapter', () => {
    let mockConfig;
    let mockEvents;
    let mockEntityName;
    let mockEntityId;
    let mockCosmosDbClient;
    beforeEach(() => {
        mockCosmosDbClient = (0, sinon_1.createStubInstance)(cosmos_1.CosmosClient, {
            database: (0, sinon_1.stub)().returns({
                container: (0, sinon_1.stub)().returns({
                    items: {
                        query: (0, sinon_1.stub)().returns({
                            fetchAll: sinon_1.fake.resolves({ resources: [] }),
                        }),
                        create: (0, sinon_1.stub)().returns(sinon_1.fake.resolves({})),
                    },
                }),
            }),
        });
        mockConfig = new framework_types_1.BoosterConfig('test');
        mockEntityName = faker_1.random.word();
        mockEntityId = faker_1.random.uuid();
        mockEvents = (0, event_helper_1.createMockEventEnvelopes)(2);
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('The "rawEventsToEnvelopes" method', () => {
        it('Generates envelopes correctly from a Cosmos DB event', async () => {
            const expectedEnvelopes = (0, event_helper_1.addMockSystemGeneratedProperties)(mockEvents);
            const cosmosDbMessage = (0, event_helper_1.wrapEventEnvelopesForCosmosDB)(expectedEnvelopes);
            const gotEnvelopes = EventsAdapter.rawEventsToEnvelopes(cosmosDbMessage);
            (0, expect_1.expect)(gotEnvelopes).to.deep.equal(expectedEnvelopes);
        });
    });
    describe('The "readEntityEventsSince" method', () => {
        it('Queries the events table to find all events related to a specific entity', async () => {
            await EventsAdapter.readEntityEventsSince(mockCosmosDbClient, mockConfig, mockEntityName, mockEntityId);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore);
            (0, expect_1.expect)(mockCosmosDbClient
                .database(mockConfig.resourceNames.applicationStack)
                .container(mockConfig.resourceNames.eventsStore).items.query).to.have.been.calledWithExactly((0, sinon_1.match)({
                query: `SELECT * FROM c WHERE c["${constants_1.eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
                    `AND c["${constants_1.eventsStoreAttributes.sortKey}"] > @fromTime ORDER BY c["${constants_1.eventsStoreAttributes.sortKey}"] ASC`,
                parameters: [
                    {
                        name: '@partitionKey',
                        value: (0, partition_keys_1.partitionKeyForEvent)(mockEntityName, mockEntityId),
                    },
                    {
                        name: '@fromTime',
                        value: sinon_1.match.defined,
                    },
                ],
            }));
        });
    });
    describe('The "readEntityLatestSnapshot" method', () => {
        it('Finds the latest entity snapshot', async () => {
            await EventsAdapter.readEntityLatestSnapshot(mockCosmosDbClient, mockConfig, mockEntityName, mockEntityId);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore);
            (0, expect_1.expect)(mockCosmosDbClient
                .database(mockConfig.resourceNames.applicationStack)
                .container(mockConfig.resourceNames.eventsStore).items.query).to.have.been.calledWithExactly((0, sinon_1.match)({
                query: `SELECT * FROM c WHERE c["${constants_1.eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
                    `ORDER BY c["${constants_1.eventsStoreAttributes.sortKey}"] DESC OFFSET 0 LIMIT 1`,
                parameters: [
                    {
                        name: '@partitionKey',
                        value: (0, partition_keys_1.partitionKeyForSnapshot)(mockEntityName, mockEntityId),
                    },
                ],
            }));
        });
    });
    describe('The "storeDispatchedEvent" method', () => {
        it('Persists the IDs of the eventEnvelopes passed via parameters', async () => {
            await EventsAdapter.storeDispatchedEvent(mockCosmosDbClient, mockEvents[0], mockConfig);
            (0, expect_1.expect)(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack);
            (0, expect_1.expect)(mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container).to.have.been.calledWithExactly(mockConfig.resourceNames.dispatchedEventsStore);
            (0, expect_1.expect)(mockCosmosDbClient
                .database(mockConfig.resourceNames.applicationStack)
                .container(mockConfig.resourceNames.dispatchedEventsStore).items.create).to.have.been.calledWithExactly((0, sinon_1.match)({ eventId: mockEvents[0].id }));
        });
    });
});
