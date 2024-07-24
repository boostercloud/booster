"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const aws_sdk_1 = require("aws-sdk");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const faker_1 = require("faker");
const src_1 = require("../../src");
const subscription_adapter_1 = require("../../src/library/subscription-adapter");
const keys_helper_1 = require("../../src/library/keys-helper");
const config = new framework_types_1.BoosterConfig('test');
describe('The "subscribeToReadModel" method', () => {
    let db;
    let envelope; // Not using the actual type to allow the `delete` operators remove non-optional properties in the tests. TypeScript 4.x.x thows a compile error in these cases.
    beforeEach(() => {
        db = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient);
        envelope = {
            connectionID: faker_1.random.uuid(),
            requestID: faker_1.random.uuid(),
            operation: {
                query: faker_1.lorem.words(5),
                variables: {
                    varOne: faker_1.lorem.word(),
                },
                operationName: faker_1.lorem.word(),
                id: faker_1.random.uuid(),
            },
            filters: {
                propOne: {
                    operation: 'eq',
                    values: [faker_1.lorem.word()],
                },
            },
            className: faker_1.lorem.word(),
            expirationTime: faker_1.random.number(10e6),
            version: 1,
        };
    });
    context('when the envelope is missing some required fields', () => {
        it('throws if the partitionKey is missing', async () => {
            delete envelope[src_1.subscriptionsStoreAttributes.partitionKey];
            const promiseResult = (0, subscription_adapter_1.subscribeToReadModel)(db, config, envelope);
            await (0, expect_1.expect)(promiseResult).to.eventually.have.been.rejectedWith(/^Subscription envelope is missing any of the following required attributes/);
            (0, expect_1.expect)(db.put).not.to.have.been.called;
        });
        it('throws if the connectionID is missing', async () => {
            delete envelope.connectionID;
            const promiseResult = (0, subscription_adapter_1.subscribeToReadModel)(db, config, envelope);
            await (0, expect_1.expect)(promiseResult).to.eventually.have.been.rejectedWith(/^Subscription envelope is missing any of the following required attributes/);
            (0, expect_1.expect)(db.put).not.to.have.been.called;
        });
        it('throws if the operation ID is missing', async () => {
            delete envelope.operation.id;
            const promiseResult = (0, subscription_adapter_1.subscribeToReadModel)(db, config, envelope);
            await (0, expect_1.expect)(promiseResult).to.eventually.have.been.rejectedWith(/^Subscription envelope is missing any of the following required attributes/);
            (0, expect_1.expect)(db.put).not.to.have.been.called;
        });
        it('throws if the ttl attribute is missing', async () => {
            delete envelope[src_1.subscriptionsStoreAttributes.ttl];
            const promiseResult = (0, subscription_adapter_1.subscribeToReadModel)(db, config, envelope);
            await (0, expect_1.expect)(promiseResult).to.eventually.have.been.rejectedWith(/^Subscription envelope is missing any of the following required attributes/);
            (0, expect_1.expect)(db.put).not.to.have.been.called;
        });
    });
    context('when the envelope is correct', () => {
        it('stores the subscription with the expected data', async () => {
            db.put = sinon_1.fake.returns({
                promise: () => Promise.resolve(),
            });
            await (0, subscription_adapter_1.subscribeToReadModel)(db, config, envelope);
            (0, expect_1.expect)(db.put).to.have.been.calledWithExactly({
                TableName: config.resourceNames.subscriptionsStore,
                Item: {
                    ...envelope,
                    [src_1.subscriptionsStoreAttributes.sortKey]: (0, keys_helper_1.sortKeyForSubscription)(envelope.connectionID, envelope.operation.id),
                    [src_1.subscriptionsStoreAttributes.indexByConnectionIDSortKey]: envelope.operation.id,
                },
            });
        });
    });
});
describe('The "fetchSubscriptions" method', () => {
    it('returns the expected subscriptions', async () => {
        const db = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient);
        const subscriptions = [
            {
                typeName: faker_1.lorem.word(),
                connectionID: faker_1.random.uuid(),
            },
            {
                typeName: faker_1.lorem.word(),
                connectionID: faker_1.random.uuid(),
            },
        ];
        db.query = sinon_1.fake.returns({
            promise: () => Promise.resolve({
                Items: subscriptions,
            }),
        });
        const subscriptionName = faker_1.lorem.word();
        const result = await (0, subscription_adapter_1.fetchSubscriptions)(db, config, subscriptionName);
        (0, expect_1.expect)(db.query).to.have.been.calledWithExactly({
            TableName: config.resourceNames.subscriptionsStore,
            ConsistentRead: true,
            KeyConditionExpression: `${src_1.subscriptionsStoreAttributes.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: { ':partitionKey': subscriptionName },
        });
        (0, expect_1.expect)(result).to.be.deep.equal(subscriptions);
    });
});
describe('The "deleteSubscription" method', () => {
    let dbQueryStub;
    let dbDeleteFake;
    let db;
    let connectionID;
    let subscriptionID;
    let queryArguments;
    beforeEach(() => {
        dbQueryStub = (0, sinon_1.stub)();
        dbQueryStub.throws('db.query called with wrong arguments');
        dbDeleteFake = (0, sinon_1.stub)().returns({ promise: () => Promise.resolve() });
        db = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient, {
            query: dbQueryStub,
            delete: dbDeleteFake,
        });
        connectionID = faker_1.random.uuid();
        subscriptionID = faker_1.random.uuid();
        queryArguments = {
            TableName: config.resourceNames.subscriptionsStore,
            IndexName: src_1.subscriptionsStoreAttributes.indexByConnectionIDName(config),
            KeyConditionExpression: `${src_1.subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey AND ` +
                `${src_1.subscriptionsStoreAttributes.indexByConnectionIDSortKey} = :sortKey`,
            ExpressionAttributeValues: {
                ':partitionKey': connectionID,
                ':sortKey': subscriptionID,
            },
        };
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('does not delete anything if there is no subscription to delete', async () => {
        dbQueryStub.withArgs(queryArguments).returns({
            promise: () => Promise.resolve({ Items: [] }),
        });
        await (0, subscription_adapter_1.deleteSubscription)(db, config, connectionID, subscriptionID);
        (0, expect_1.expect)(db.delete).not.to.have.been.called;
    });
    it('deletes the right subscription', async () => {
        const foundSubscription = {
            className: faker_1.random.alphaNumeric(10),
            connectionID,
            subscriptionID,
            connectionID_subscriptionID: (0, keys_helper_1.sortKeyForSubscription)(connectionID, subscriptionID),
        };
        dbQueryStub.withArgs(queryArguments).returns({
            promise: () => Promise.resolve({
                Items: [foundSubscription],
            }),
        });
        await (0, subscription_adapter_1.deleteSubscription)(db, config, connectionID, subscriptionID);
        (0, expect_1.expect)(dbDeleteFake).to.have.been.calledWithExactly({
            TableName: config.resourceNames.subscriptionsStore,
            Key: {
                [src_1.subscriptionsStoreAttributes.partitionKey]: foundSubscription.className,
                [src_1.subscriptionsStoreAttributes.sortKey]: foundSubscription.connectionID_subscriptionID,
            },
        });
    });
});
describe('The "deleteAllSubscription" method', () => {
    let dbQueryStub;
    let dbBatchWriteFake;
    let db;
    let connectionID;
    let queryArguments;
    beforeEach(() => {
        dbQueryStub = (0, sinon_1.stub)();
        dbQueryStub.throws('db.query called with wrong arguments');
        dbBatchWriteFake = (0, sinon_1.stub)().returns({ promise: () => Promise.resolve() });
        db = (0, sinon_1.createStubInstance)(aws_sdk_1.DynamoDB.DocumentClient, {
            query: dbQueryStub,
            batchWrite: dbBatchWriteFake,
        });
        connectionID = faker_1.random.uuid();
        queryArguments = {
            TableName: config.resourceNames.subscriptionsStore,
            IndexName: src_1.subscriptionsStoreAttributes.indexByConnectionIDName(config),
            KeyConditionExpression: `${src_1.subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey`,
            ExpressionAttributeValues: { ':partitionKey': connectionID },
        };
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    it('does not delete anything if there is no subscription to delete', async () => {
        dbQueryStub.withArgs(queryArguments).returns({
            promise: () => Promise.resolve({ Items: [] }),
        });
        await (0, subscription_adapter_1.deleteAllSubscriptions)(db, config, connectionID);
        (0, expect_1.expect)(db.batchWrite).not.to.have.been.called;
    });
    it('deletes all the expected subscriptions', async () => {
        const subscriptionIDOne = faker_1.random.uuid();
        const subscriptionIDTwo = faker_1.random.uuid();
        const foundSubscriptions = [
            {
                className: faker_1.random.alphaNumeric(10),
                connectionID,
                subscriptionID: subscriptionIDOne,
                connectionID_subscriptionID: (0, keys_helper_1.sortKeyForSubscription)(connectionID, subscriptionIDOne),
            },
            {
                className: faker_1.random.alphaNumeric(10),
                connectionID,
                subscriptionID: subscriptionIDTwo,
                connectionID_subscriptionID: (0, keys_helper_1.sortKeyForSubscription)(connectionID, subscriptionIDTwo),
            },
        ];
        dbQueryStub.withArgs(queryArguments).returns({
            promise: () => Promise.resolve({
                Items: foundSubscriptions,
            }),
        });
        await (0, subscription_adapter_1.deleteAllSubscriptions)(db, config, connectionID);
        (0, expect_1.expect)(dbBatchWriteFake).to.have.been.calledWithExactly({
            RequestItems: {
                [config.resourceNames.subscriptionsStore]: foundSubscriptions.map((subscriptionRecord) => ({
                    DeleteRequest: {
                        Key: {
                            [src_1.subscriptionsStoreAttributes.partitionKey]: subscriptionRecord.className,
                            [src_1.subscriptionsStoreAttributes.sortKey]: subscriptionRecord.connectionID_subscriptionID,
                        },
                    },
                })),
            },
        });
    });
});
