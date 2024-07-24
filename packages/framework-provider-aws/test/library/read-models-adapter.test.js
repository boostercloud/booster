"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const expect_1 = require("../expect");
const sinon_1 = require("sinon");
const read_models_adapter_1 = require("../../src/library/read-models-adapter");
const aws_sdk_1 = require("aws-sdk");
const framework_types_1 = require("@boostercloud/framework-types");
describe('the "rawReadModelEventsToEnvelopes" method', () => {
    const config = new framework_types_1.BoosterConfig('test');
    config.appName = 'test-app';
    it('fails when some event does not have the required field "eventSourceARN"', async () => {
        const events = {
            Records: [
                {
                    // A well formed event
                    eventSourceARN: generateReadModelTableARN(config, 'CartReadModel'),
                    dynamodb: { NewImage: { id: { S: 'test' } } },
                },
                {
                    // An event with missing required fields
                    eventSourceARN: undefined,
                    dynamodb: { NewImage: { id: { S: 'test' } } },
                },
            ],
        };
        await (0, expect_1.expect)((0, read_models_adapter_1.rawReadModelEventsToEnvelopes)(config, events)).to.be.eventually.rejectedWith(/Received a DynamoDB stream event without/);
    });
    it('fails when some event does not have the required field "NewImage"', async () => {
        const events = {
            Records: [
                {
                    // A well formed event
                    eventSourceARN: generateReadModelTableARN(config, 'CartReadModel'),
                    dynamodb: { NewImage: { id: { S: 'test' } } },
                },
                {
                    // An event with missing required fields
                    eventSourceARN: generateReadModelTableARN(config, 'CartReadModel'),
                    dynamodb: { NewImage: undefined },
                },
            ],
        };
        await (0, expect_1.expect)((0, read_models_adapter_1.rawReadModelEventsToEnvelopes)(config, events)).to.be.eventually.rejectedWith(/Received a DynamoDB stream event without/);
    });
    it('returns the envelopes correctly', async () => {
        const expectedReadModelOne = {
            typeName: 'ReadModelOne',
            value: {
                id: 'one',
                aField: 123,
            },
        };
        const expectedReadModelTwo = {
            typeName: 'ReadModelTwo',
            value: {
                id: 'two',
                aField: 456,
            },
        };
        const events = {
            Records: [
                {
                    eventSourceARN: generateReadModelTableARN(config, 'ReadModelOne'),
                    dynamodb: {
                        NewImage: {
                            id: { S: expectedReadModelOne.value.id.toString() },
                            aField: { N: `${expectedReadModelOne.value.aField}` },
                        },
                    },
                },
                {
                    eventSourceARN: generateReadModelTableARN(config, 'ReadModelTwo'),
                    dynamodb: {
                        NewImage: {
                            id: { S: expectedReadModelTwo.value.id.toString() },
                            aField: { N: `${expectedReadModelTwo.value.aField}` },
                        },
                    },
                },
            ],
        };
        await (0, expect_1.expect)((0, read_models_adapter_1.rawReadModelEventsToEnvelopes)(config, events)).to.eventually.become([
            expectedReadModelOne,
            expectedReadModelTwo,
        ]);
    });
});
describe('the "fetchReadModel" method', () => {
    context("when the read model doesn't exist", () => {
        context('when no sequenceMetadata is defined', () => {
            it('responds with an error querying by partition key', async () => {
                const db = new aws_sdk_1.DynamoDB.DocumentClient();
                const config = new framework_types_1.BoosterConfig('test');
                (0, sinon_1.replace)(db, 'query', sinon_1.fake.returns({
                    promise: sinon_1.fake.rejects('not found'),
                }));
                await (0, expect_1.expect)((0, read_models_adapter_1.fetchReadModel)(db, config, 'SomeReadModel', 'someReadModelID')).to.be.eventually.rejectedWith('not found');
                (0, expect_1.expect)(db.query).to.have.been.calledOnceWith({
                    TableName: 'new-booster-app-app-SomeReadModel',
                    KeyConditionExpression: '#id = :id',
                    ExpressionAttributeNames: {
                        '#id': 'id',
                    },
                    ExpressionAttributeValues: {
                        ':id': 'someReadModelID',
                    },
                    ConsistentRead: true,
                });
            });
        });
        context('when sequenceMetadata is defined', () => {
            it('responds with an error querying both by partition and sort keys', async () => {
                const db = new aws_sdk_1.DynamoDB.DocumentClient();
                const config = new framework_types_1.BoosterConfig('test');
                (0, sinon_1.replace)(db, 'query', sinon_1.fake.returns({
                    promise: sinon_1.fake.rejects('not found'),
                }));
                await (0, expect_1.expect)((0, read_models_adapter_1.fetchReadModel)(db, config, 'SomeReadModel', 'someReadModelID', { name: 'asdf', value: '42' })).to.be.eventually.rejectedWith('not found');
                (0, expect_1.expect)(db.query).to.have.been.calledOnceWith({
                    TableName: 'new-booster-app-app-SomeReadModel',
                    KeyConditionExpression: '#id = :id AND #asdf = :asdf',
                    ExpressionAttributeNames: {
                        '#id': 'id',
                        '#asdf': 'asdf',
                    },
                    ExpressionAttributeValues: {
                        ':id': 'someReadModelID',
                        ':asdf': '42',
                    },
                    ConsistentRead: true,
                });
            });
        });
    });
    context('when the read model exists', () => {
        context('when no sequenceMetadata is defined', () => {
            it('gets the read model by partition key and responds with a read model', async () => {
                const db = new aws_sdk_1.DynamoDB.DocumentClient();
                const config = new framework_types_1.BoosterConfig('test');
                (0, sinon_1.replace)(db, 'query', sinon_1.fake.returns({
                    promise: sinon_1.fake.resolves({ Items: [{ some: 'object' }] }),
                }));
                const results = await (0, read_models_adapter_1.fetchReadModel)(db, config, 'SomeReadModel', 'someReadModelID');
                (0, expect_1.expect)(db.query).to.have.been.calledOnceWith({
                    TableName: 'new-booster-app-app-SomeReadModel',
                    KeyConditionExpression: '#id = :id',
                    ExpressionAttributeNames: {
                        '#id': 'id',
                    },
                    ExpressionAttributeValues: {
                        ':id': 'someReadModelID',
                    },
                    ConsistentRead: true,
                });
                (0, expect_1.expect)(results).to.deep.equal([
                    {
                        boosterMetadata: {
                            optimisticConcurrencyValue: 1,
                        },
                        some: 'object',
                    },
                ]);
            });
        });
        context('when sequenceMetadata is defined', () => {
            it('gets the read model by partition and sort key and responds with a read model', async () => {
                const db = new aws_sdk_1.DynamoDB.DocumentClient();
                const config = new framework_types_1.BoosterConfig('test');
                (0, sinon_1.replace)(db, 'query', sinon_1.fake.returns({
                    promise: sinon_1.fake.resolves({ Items: [{ some: 'object', time: '42' }] }),
                }));
                const results = await (0, read_models_adapter_1.fetchReadModel)(db, config, 'SomeReadModel', 'someReadModelID', {
                    name: 'time',
                    value: '42',
                });
                (0, expect_1.expect)(db.query).to.have.been.calledOnceWith({
                    TableName: 'new-booster-app-app-SomeReadModel',
                    KeyConditionExpression: '#id = :id AND #time = :time',
                    ExpressionAttributeNames: {
                        '#id': 'id',
                        '#time': 'time',
                    },
                    ExpressionAttributeValues: {
                        ':id': 'someReadModelID',
                        ':time': '42',
                    },
                    ConsistentRead: true,
                });
                (0, expect_1.expect)(results).to.deep.equal([
                    {
                        boosterMetadata: {
                            optimisticConcurrencyValue: 1,
                        },
                        some: 'object',
                        time: '42',
                    },
                ]);
            });
        });
    });
});
describe('the "storeReadModel" method', () => {
    const db = new aws_sdk_1.DynamoDB.DocumentClient();
    const config = new framework_types_1.BoosterConfig('test');
    beforeEach(() => {
        (0, sinon_1.restore)();
    });
    it('saves a read model', async () => {
        (0, sinon_1.replace)(db, 'put', sinon_1.fake.returns({
            promise: sinon_1.fake.resolves({
                $response: {},
            }),
        }));
        const something = await (0, read_models_adapter_1.storeReadModel)(db, config, 'SomeReadModel', { id: 777, some: 'object' }, 0);
        (0, expect_1.expect)(db.put).to.have.been.calledOnceWithExactly({
            TableName: 'new-booster-app-app-SomeReadModel',
            Item: { id: 777, some: 'object' },
            ConditionExpression: 'attribute_not_exists(boosterMetadata.optimisticConcurrencyValue) OR boosterMetadata.optimisticConcurrencyValue = :optimisticConcurrencyValue',
            ExpressionAttributeValues: { ':optimisticConcurrencyValue': 0 },
        });
        (0, expect_1.expect)(something).not.to.be.null;
    });
    it('throws the OptimisticConcurrencyUnexpectedVersionError when there is a ConditionalCheckFailedException', async () => {
        (0, sinon_1.replace)(db, 'put', sinon_1.fake.returns({
            promise: () => {
                const e = new Error('test error');
                e.name = 'ConditionalCheckFailedException';
                throw e;
            },
        }));
        await (0, expect_1.expect)((0, read_models_adapter_1.storeReadModel)(db, config, 'SomeReadModel', { id: 777, some: 'object' }, 0)).to.eventually.be.rejectedWith(framework_types_1.OptimisticConcurrencyUnexpectedVersionError);
    });
});
describe('the "deleteReadModel"', () => {
    context('when the read model is not sequenced', () => {
        it('deletes an existing read model by the partition key', async () => {
            const db = new aws_sdk_1.DynamoDB.DocumentClient();
            const config = new framework_types_1.BoosterConfig('test');
            (0, sinon_1.replace)(db, 'delete', sinon_1.fake.returns({
                promise: sinon_1.fake.resolves({
                    $response: {},
                }),
            }));
            await (0, read_models_adapter_1.deleteReadModel)(db, config, 'SomeReadModel', { id: 777, some: 'object' });
            (0, expect_1.expect)(db.delete).to.have.been.calledOnceWithExactly({
                TableName: 'new-booster-app-app-SomeReadModel',
                Key: { id: 777 },
            });
        });
    });
    context('when the read model is sequenced', () => {
        it('deletes an existing read model by both the partition and the sort keys', async () => {
            const db = new aws_sdk_1.DynamoDB.DocumentClient();
            const config = new framework_types_1.BoosterConfig('test');
            const readModelName = 'SomeReadModel';
            config.readModelSequenceKeys[readModelName] = 'time';
            (0, sinon_1.replace)(db, 'delete', sinon_1.fake.returns({
                promise: sinon_1.fake.resolves({
                    $response: {},
                }),
            }));
            await (0, read_models_adapter_1.deleteReadModel)(db, config, readModelName, { id: '777', time: '42', some: 'object' });
            (0, expect_1.expect)(db.delete).to.have.been.calledOnceWithExactly({
                TableName: 'new-booster-app-app-SomeReadModel',
                Key: { id: '777', time: '42' },
            });
        });
    });
});
function generateReadModelTableARN(config, readModelName) {
    return `arn:aws:dynamodb:eu-west-1:123456:table/${config.resourceNames.forReadModel(readModelName)}`;
}
