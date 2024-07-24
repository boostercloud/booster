"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const faker_1 = require("faker");
const aws_sdk_1 = require("aws-sdk");
const events_searcher_adapter_1 = require("../../src/library/events-searcher-adapter");
const src_1 = require("../../src");
const keys_helper_1 = require("../../src/library/keys-helper");
const rewire = require('rewire');
describe('Events searcher adapter', () => {
    const config = new framework_types_1.BoosterConfig('test');
    let db;
    beforeEach(() => {
        db = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient, {
            query: {
                promise: sinon_1.fake.returns({
                    Items: [],
                }),
            },
            batchGet: {
                promise: sinon_1.fake.returns({
                    Responses: {},
                }),
            },
        });
    });
    after(() => {
        (0, sinon_1.restore)();
    });
    describe('The "searchEvents" method', () => {
        it('throws an error when an invalid search is made', async () => {
            await (0, expect_1.expect)((0, events_searcher_adapter_1.searchEvents)(db, config, {})).to.be.rejectedWith(/Invalid search event query/);
        });
        describe('for a search by entity with ID', () => {
            let filter;
            let entityID;
            beforeEach(() => {
                entityID = faker_1.random.uuid();
                filter = {
                    entity: faker_1.random.alpha(),
                    entityID: entityID,
                };
            });
            runQueryTestsWithTimeFiltersVariants(() => filter, () => {
                return {
                    TableName: config.resourceNames.eventsStore,
                    ConsistentRead: true,
                    ScanIndexForward: false,
                    Limit: undefined,
                    KeyConditionExpression: `${src_1.eventsStoreAttributes.partitionKey} = :partitionKey`,
                    ExpressionAttributeValues: { ':partitionKey': (0, keys_helper_1.partitionKeyForEvent)(filter.entity, entityID) },
                };
            });
        });
        describe('for a search by entity and no ID', () => {
            let filter;
            beforeEach(() => {
                filter = {
                    entity: faker_1.random.alpha(),
                };
            });
            runQueryTestsWithTimeFiltersVariants(() => filter, () => {
                return {
                    TableName: config.resourceNames.eventsStore,
                    IndexName: src_1.eventsStoreAttributes.indexByEntity.name(config),
                    ScanIndexForward: false,
                    Limit: undefined,
                    KeyConditionExpression: `${src_1.eventsStoreAttributes.indexByEntity.partitionKey} = :partitionKey`,
                    ExpressionAttributeValues: {
                        ':partitionKey': (0, keys_helper_1.partitionKeyForIndexByEntity)(filter.entity, 'event'),
                    },
                };
            }, true);
        });
        describe('for a search by type', () => {
            let filter;
            beforeEach(() => {
                filter = {
                    type: faker_1.random.alpha(),
                };
            });
            runQueryTestsWithTimeFiltersVariants(() => filter, () => {
                return {
                    TableName: config.resourceNames.eventsStore,
                    IndexName: src_1.eventsStoreAttributes.indexByType.name(config),
                    ScanIndexForward: false,
                    Limit: undefined,
                    KeyConditionExpression: `${src_1.eventsStoreAttributes.indexByType.partitionKey} = :partitionKey`,
                    ExpressionAttributeValues: {
                        ':partitionKey': filter.type,
                    },
                };
            }, true);
        });
    });
    function runQueryTestsWithTimeFiltersVariants(getFilters, getQuery, requiresExtraQueryToMainTable = false) {
        context('with no time filters', () => {
            it('does the query with no time filters', async () => {
                await (0, events_searcher_adapter_1.searchEvents)(db, config, getFilters());
                (0, expect_1.expect)(db.query).to.have.been.calledWithExactly(getQuery());
            });
        });
        context('with "from" time filter and limit', () => {
            let filterWithFrom;
            let queryWithFromTimeAdditions;
            beforeEach(() => {
                filterWithFrom = getFilters();
                filterWithFrom.from = faker_1.date.recent().toISOString();
                filterWithFrom.limit = 3;
                queryWithFromTimeAdditions = getQuery();
                queryWithFromTimeAdditions.KeyConditionExpression += ` AND ${src_1.eventsStoreAttributes.sortKey} >= :fromTime`;
                queryWithFromTimeAdditions.ExpressionAttributeValues[':fromTime'] = filterWithFrom.from;
                queryWithFromTimeAdditions.Limit = filterWithFrom.limit;
            });
            it('does the query with "from" time filter and limit', async () => {
                await (0, events_searcher_adapter_1.searchEvents)(db, config, filterWithFrom);
                (0, expect_1.expect)(db.query).to.have.been.calledWithExactly(queryWithFromTimeAdditions);
            });
        });
        context('with "from" time filter', () => {
            let filterWithFrom;
            let queryWithFromTimeAdditions;
            beforeEach(() => {
                filterWithFrom = getFilters();
                filterWithFrom.from = faker_1.date.recent().toISOString();
                queryWithFromTimeAdditions = getQuery();
                queryWithFromTimeAdditions.KeyConditionExpression += ` AND ${src_1.eventsStoreAttributes.sortKey} >= :fromTime`;
                queryWithFromTimeAdditions.ExpressionAttributeValues[':fromTime'] = filterWithFrom.from;
            });
            it('does the query with "from" time filter', async () => {
                await (0, events_searcher_adapter_1.searchEvents)(db, config, filterWithFrom);
                (0, expect_1.expect)(db.query).to.have.been.calledWithExactly(queryWithFromTimeAdditions);
            });
        });
        context('with "to" time filters', () => {
            let filterWithTo;
            let queryWithToTimeAdditions;
            beforeEach(() => {
                filterWithTo = getFilters();
                filterWithTo.to = faker_1.date.soon().toISOString();
                queryWithToTimeAdditions = getQuery();
                queryWithToTimeAdditions.KeyConditionExpression += ` AND ${src_1.eventsStoreAttributes.sortKey} <= :toTime`;
                queryWithToTimeAdditions.ExpressionAttributeValues[':toTime'] = filterWithTo.to;
            });
            it('does the query with "to" time filters', async () => {
                await (0, events_searcher_adapter_1.searchEvents)(db, config, filterWithTo);
                (0, expect_1.expect)(db.query).to.have.been.calledWithExactly(queryWithToTimeAdditions);
            });
        });
        context('with both time filters', () => {
            let fullFilter;
            let fullQuery;
            beforeEach(() => {
                fullFilter = getFilters();
                fullFilter.from = faker_1.date.recent().toISOString();
                fullFilter.to = faker_1.date.soon().toISOString();
                fullQuery = getQuery();
                fullQuery.KeyConditionExpression += ` AND ${src_1.eventsStoreAttributes.sortKey} BETWEEN :fromTime AND :toTime`;
                fullQuery.ExpressionAttributeValues[':fromTime'] = fullFilter.from;
                fullQuery.ExpressionAttributeValues[':toTime'] = fullFilter.to;
            });
            it('does the query with both time filters', async () => {
                await (0, events_searcher_adapter_1.searchEvents)(db, config, fullFilter);
                (0, expect_1.expect)(db.query).to.have.been.calledWithExactly(fullQuery);
            });
        });
        if (requiresExtraQueryToMainTable) {
            it('does an extra query to the main table with the corresponding keys', async () => {
                const firstQueryResponse = [
                    {
                        [src_1.eventsStoreAttributes.partitionKey]: faker_1.random.alpha(),
                        [src_1.eventsStoreAttributes.sortKey]: faker_1.random.alphaNumeric(),
                    },
                    {
                        [src_1.eventsStoreAttributes.partitionKey]: faker_1.random.alpha(),
                        [src_1.eventsStoreAttributes.sortKey]: faker_1.random.alphaNumeric(),
                    },
                ];
                (0, sinon_1.replace)(db, 'query', sinon_1.fake.returns({
                    promise: sinon_1.fake.returns({
                        Items: firstQueryResponse,
                    }),
                }));
                await (0, events_searcher_adapter_1.searchEvents)(db, config, getFilters());
                (0, expect_1.expect)(db.batchGet).to.have.been.calledWithExactly({
                    RequestItems: {
                        [config.resourceNames.eventsStore]: {
                            ConsistentRead: true,
                            Keys: firstQueryResponse.map((record) => {
                                return {
                                    [src_1.eventsStoreAttributes.partitionKey]: record[src_1.eventsStoreAttributes.partitionKey],
                                    [src_1.eventsStoreAttributes.sortKey]: record[src_1.eventsStoreAttributes.sortKey],
                                };
                            }),
                        },
                    },
                });
            });
        }
        context('with an unsorted page of result items', () => {
            const rewiredModule = rewire('../../src/library/events-searcher-adapter');
            const occurredThirdID = faker_1.random.uuid(), occurredSecondID = faker_1.random.uuid(), occurredFirstID = faker_1.random.uuid();
            const occurredThirdDate = faker_1.date.recent(), occurredSecondDate = faker_1.date.recent(10, occurredThirdDate), occurredFirstDate = faker_1.date.recent(10, occurredSecondDate);
            const unsortedResult = [
                buildEventEnvelope(occurredThirdID, occurredThirdDate.toISOString()),
                buildEventEnvelope(occurredFirstID, occurredFirstDate.toISOString()),
                buildEventEnvelope(occurredSecondID, occurredSecondDate.toISOString()),
            ];
            const fakeExecuteSearch = sinon_1.fake.returns(Promise.resolve(unsortedResult));
            let revert;
            beforeEach(() => {
                revert = rewiredModule.__set__('executeSearch', fakeExecuteSearch);
            });
            afterEach(() => {
                revert();
            });
            it('the result is converted and sorted in descendant order', async () => {
                // For extra care, first assert that the result page is truly unordered
                (0, expect_1.expect)(unsortedResult.map((item) => item.entityID)).not.to.be.deep.equal([
                    occurredThirdID,
                    occurredSecondID,
                    occurredFirstID,
                ]);
                const res = await rewiredModule.searchEvents(db, config, getFilters());
                console.log(res);
                // Check they are sorted
                (0, expect_1.expect)(res.map((item) => item.entityID)).to.be.deep.equal([occurredThirdID, occurredSecondID, occurredFirstID]);
                // Check they have the right structure
                for (const item of res) {
                    (0, expect_1.expect)(item).to.have.keys(['type', 'entity', 'entityID', 'requestID', 'user', 'createdAt', 'value']);
                }
            });
        });
    }
});
function buildEventEnvelope(id, createdAt) {
    return {
        entityID: id,
        createdAt,
        requestID: faker_1.random.uuid(),
        value: { id: faker_1.random.uuid() },
        entityTypeName: faker_1.random.alpha(),
        typeName: faker_1.random.alpha(),
        kind: 'event',
        superKind: 'domain',
        version: faker_1.random.number(),
    };
}
