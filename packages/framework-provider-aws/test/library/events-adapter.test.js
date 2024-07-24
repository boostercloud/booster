"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
const expect_1 = require("../expect");
const Library = require("../../src/library/events-adapter");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const aws_sdk_1 = require("aws-sdk");
const src_1 = require("../../src");
const keys_helper_1 = require("../../src/library/keys-helper");
const dynamodb_1 = require("aws-sdk/clients/dynamodb");
describe('the events-adapter', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('the `rawEventsToEnvelopes` method', () => {
        it('generates envelopes correctly from an AWS Kinesis event', async () => {
            const expectedEnvelopes = buildEventEnvelopes();
            const kinesisMessage = wrapEventEnvelopesForDynamoDB(expectedEnvelopes);
            const gotEnvelopes = Library.rawEventsToEnvelopes(kinesisMessage);
            (0, expect_1.expect)(gotEnvelopes).to.be.deep.equal(expectedEnvelopes);
        });
    });
    describe('the `readEntityEventsSince` method', () => {
        it('queries the events table to find all events related to a specific entity', async () => {
            const dynamoDB = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient);
            dynamoDB.query = sinon_1.fake.returns({
                promise: sinon_1.fake.resolves({
                    Items: [],
                }),
            });
            const config = new framework_types_1.BoosterConfig('test');
            config.appName = 'nuke-button';
            await Library.readEntityEventsSince(dynamoDB, config, 'SomeEntity', 'someSpecialID');
            (0, expect_1.expect)(dynamoDB.query).to.have.been.calledWith((0, sinon_1.match)({
                TableName: 'nuke-button-app-events-store',
                ConsistentRead: true,
                KeyConditionExpression: `${src_1.eventsStoreAttributes.partitionKey} = :partitionKey AND ${src_1.eventsStoreAttributes.sortKey} > :fromTime`,
                ExpressionAttributeValues: {
                    ':partitionKey': (0, keys_helper_1.partitionKeyForEvent)('SomeEntity', 'someSpecialID'),
                    ':fromTime': sinon_1.match.defined,
                },
                ScanIndexForward: true,
            }));
        });
    });
    describe('the `readEntityLatestSnapshot` method', () => {
        it('finds the latest entity snapshot', async () => {
            const dynamoDB = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient);
            dynamoDB.query = sinon_1.fake.returns({ promise: sinon_1.fake.resolves('') });
            const config = new framework_types_1.BoosterConfig('test');
            config.appName = 'nuke-button';
            await Library.readEntityLatestSnapshot(dynamoDB, config, 'SomeEntity', 'someSpecialID');
            (0, expect_1.expect)(dynamoDB.query).to.have.been.calledWith((0, sinon_1.match)({
                TableName: 'nuke-button-app-events-store',
                ConsistentRead: true,
                KeyConditionExpression: `${src_1.eventsStoreAttributes.partitionKey} = :partitionKey`,
                ExpressionAttributeValues: {
                    ':partitionKey': (0, keys_helper_1.partitionKeyForEntitySnapshot)('SomeEntity', 'someSpecialID'),
                },
                ScanIndexForward: false,
                Limit: 1,
            }));
        });
    });
    describe('the `storeEvents` method', () => {
        it('publishes the eventEnvelopes passed via parameter', async () => {
            const config = new framework_types_1.BoosterConfig('test');
            config.appName = 'test-app';
            const requestID = 'request-id';
            const events = [
                {
                    entityID() {
                        return '123';
                    },
                },
                {
                    entityID() {
                        return '456';
                    },
                },
            ];
            const fakePut = sinon_1.fake.returns({
                promise: sinon_1.fake.resolves(''),
            });
            const fakeDynamo = { put: fakePut };
            const eventEnvelopes = events.map((e) => {
                return {
                    version: 1,
                    kind: 'event',
                    superKind: 'domain',
                    requestID,
                    entityID: e.entityID(),
                    entityTypeName: 'fake-entity-name',
                    typeName: 'fake-type-name',
                    value: {
                        entityID: e.entityID,
                    },
                };
            });
            await Library.storeEvents(fakeDynamo, eventEnvelopes, config);
            (0, expect_1.expect)(fakePut).to.be.calledTwice;
            for (const eventEnvelope of eventEnvelopes) {
                const partitionKey = (0, keys_helper_1.partitionKeyForEvent)(eventEnvelope.entityTypeName, eventEnvelope.entityID);
                (0, expect_1.expect)(fakePut).to.be.calledWithExactly({
                    TableName: config.resourceNames.eventsStore,
                    ConditionExpression: `${src_1.eventsStoreAttributes.partitionKey} <> :partitionKey AND ${src_1.eventsStoreAttributes.sortKey} <> :sortKey`,
                    ExpressionAttributeValues: {
                        ':partitionKey': partitionKey,
                        ':sortKey': sinon_1.match.string,
                    },
                    Item: {
                        ...eventEnvelope,
                        [src_1.eventsStoreAttributes.partitionKey]: partitionKey,
                        [src_1.eventsStoreAttributes.sortKey]: sinon_1.match.string,
                        [src_1.eventsStoreAttributes.indexByEntity.partitionKey]: (0, keys_helper_1.partitionKeyForIndexByEntity)(eventEnvelope.entityTypeName, eventEnvelope.kind),
                    },
                });
            }
        });
    });
});
function buildEventEnvelopes() {
    return [
        {
            version: 1,
            entityID: 'id',
            kind: 'event',
            superKind: 'domain',
            value: {
                id: 'id',
            },
            typeName: 'EventName',
            entityTypeName: 'EntityName',
            requestID: 'requestID',
        },
        {
            version: 1,
            entityID: 'id2',
            kind: 'event',
            superKind: 'domain',
            value: {
                id: 'id2',
            },
            typeName: 'EventName2',
            entityTypeName: 'EntityName2',
            requestID: 'requestID2',
        },
    ];
}
function wrapEventEnvelopesForDynamoDB(eventEnvelopes) {
    const dynamoMessage = {
        Records: eventEnvelopes.map((envelope) => ({
            dynamodb: {
                NewImage: dynamodb_1.Converter.marshall(envelope),
            },
        })),
    };
    return dynamoMessage;
}
