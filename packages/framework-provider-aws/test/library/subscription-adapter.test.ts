/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { DynamoDB } from 'aws-sdk'
import { fake, createStubInstance, stub, restore, SinonStub } from 'sinon'
import { Logger, SubscriptionEnvelope, BoosterConfig } from '@boostercloud/framework-types'
import { random, lorem } from 'faker'
import { subscriptionsStoreAttributes } from '../../src'
import {
  deleteAllSubscriptions,
  deleteSubscription,
  fetchSubscriptions,
  subscribeToReadModel,
  SubscriptionIndexRecord,
} from '../../src/library/subscription-adapter'
import { sortKeyForSubscription } from '../../src/library/keys-helper'
import { DocumentClient } from 'aws-sdk/lib/dynamodb/document_client'

const logger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}
const config = new BoosterConfig('test')

describe('The "subscribeToReadModel" method', () => {
  let db: DynamoDB.DocumentClient
  let envelope: any // Not using the actual type to allow the `delete` operators remove non-optional properties in the tests. TypeScript 4.x.x thows a compile error in these cases.

  beforeEach(() => {
    db = createStubInstance(DynamoDB.DocumentClient)
    envelope = {
      connectionID: random.uuid(),
      requestID: random.uuid(),
      operation: {
        query: lorem.words(5),
        variables: {
          varOne: lorem.word(),
        },
        operationName: lorem.word(),
        id: random.uuid(),
      },
      filters: {
        propOne: {
          operation: 'eq',
          values: [lorem.word()],
        },
      },
      typeName: lorem.word(),
      expirationTime: random.number(10e6),
      version: 1,
    }
  })

  context('when the envelope is missing some required fields', () => {
    it('throws if the partitionKey is missing', async () => {
      delete envelope[subscriptionsStoreAttributes.partitionKey]
      const promiseResult = subscribeToReadModel(db, config, logger, envelope)
      await expect(promiseResult).to.eventually.have.been.rejectedWith(
        /^Subscription envelope is missing any of the following required attributes/
      )
      expect(db.put).not.to.have.been.called
    })

    it('throws if the connectionID is missing', async () => {
      delete envelope.connectionID
      const promiseResult = subscribeToReadModel(db, config, logger, envelope)
      await expect(promiseResult).to.eventually.have.been.rejectedWith(
        /^Subscription envelope is missing any of the following required attributes/
      )
      expect(db.put).not.to.have.been.called
    })

    it('throws if the operation ID is missing', async () => {
      delete envelope.operation.id
      const promiseResult = subscribeToReadModel(db, config, logger, envelope)
      await expect(promiseResult).to.eventually.have.been.rejectedWith(
        /^Subscription envelope is missing any of the following required attributes/
      )
      expect(db.put).not.to.have.been.called
    })

    it('throws if the ttl attribute is missing', async () => {
      delete envelope[subscriptionsStoreAttributes.ttl]
      const promiseResult = subscribeToReadModel(db, config, logger, envelope)
      await expect(promiseResult).to.eventually.have.been.rejectedWith(
        /^Subscription envelope is missing any of the following required attributes/
      )
      expect(db.put).not.to.have.been.called
    })
  })

  context('when the envelope is correct', () => {
    it('stores the subscription with the expected data', async () => {
      db.put = fake.returns({
        promise: () => Promise.resolve(),
      })
      await subscribeToReadModel(db, config, logger, envelope)
      expect(db.put).to.have.been.calledWithExactly({
        TableName: config.resourceNames.subscriptionsStore,
        Item: {
          ...envelope,
          [subscriptionsStoreAttributes.sortKey]: sortKeyForSubscription(envelope.connectionID, envelope.operation.id!),
          [subscriptionsStoreAttributes.indexByConnectionIDSortKey]: envelope.operation.id,
        },
      } as DocumentClient.PutItemInput)
    })
  })
})

describe('The "fetchSubscriptions" method', () => {
  it('returns the expected subscriptions', async () => {
    const db: DynamoDB.DocumentClient = createStubInstance(DynamoDB.DocumentClient)
    const subscriptions: Array<SubscriptionEnvelope> = [
      {
        typeName: lorem.word(),
        connectionID: random.uuid(),
      } as any,
      {
        typeName: lorem.word(),
        connectionID: random.uuid(),
      } as any,
    ]
    db.query = fake.returns({
      promise: () =>
        Promise.resolve({
          Items: subscriptions,
        }),
    })
    const subscriptionName = lorem.word()
    const result = await fetchSubscriptions(db, config, logger, subscriptionName)

    expect(db.query).to.have.been.calledWithExactly({
      TableName: config.resourceNames.subscriptionsStore,
      ConsistentRead: true,
      KeyConditionExpression: `${subscriptionsStoreAttributes.partitionKey} = :partitionKey`,
      ExpressionAttributeValues: { ':partitionKey': subscriptionName },
    } as DocumentClient.QueryInput)
    expect(result).to.be.deep.equal(subscriptions)
  })
})

describe('The "deleteSubscription" method', () => {
  let dbQueryStub: SinonStub
  let dbDeleteFake: SinonStub
  let db: DynamoDB.DocumentClient
  let connectionID: string
  let subscriptionID: string
  let queryArguments: DocumentClient.QueryInput

  beforeEach(() => {
    dbQueryStub = stub()
    dbQueryStub.throws('db.query called with wrong arguments')
    dbDeleteFake = stub().returns({ promise: () => Promise.resolve() })
    db = createStubInstance(DynamoDB.DocumentClient, {
      query: dbQueryStub as any,
      delete: dbDeleteFake as any,
    })
    connectionID = random.uuid()
    subscriptionID = random.uuid()
    queryArguments = {
      TableName: config.resourceNames.subscriptionsStore,
      IndexName: subscriptionsStoreAttributes.indexByConnectionIDName(config),
      KeyConditionExpression:
        `${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey AND ` +
        `${subscriptionsStoreAttributes.indexByConnectionIDSortKey} = :sortKey`,
      ExpressionAttributeValues: {
        ':partitionKey': connectionID,
        ':sortKey': subscriptionID,
      },
    }
  })
  afterEach(() => {
    restore()
  })

  it('does not delete anything if there is no subscription to delete', async () => {
    dbQueryStub.withArgs(queryArguments).returns({
      promise: () => Promise.resolve({ Items: [] }),
    })

    await deleteSubscription(db, config, logger, connectionID, subscriptionID)
    expect(db.delete).not.to.have.been.called
  })

  it('deletes the right subscription', async () => {
    const foundSubscription: SubscriptionIndexRecord = {
      typeName: random.alphaNumeric(10),
      connectionID,
      subscriptionID,
      // eslint-disable-next-line @typescript-eslint/camelcase
      connectionID_subscriptionID: sortKeyForSubscription(connectionID, subscriptionID),
    }
    dbQueryStub.withArgs(queryArguments).returns({
      promise: () =>
        Promise.resolve({
          Items: [foundSubscription],
        }),
    })

    await deleteSubscription(db, config, logger, connectionID, subscriptionID)

    expect(dbDeleteFake).to.have.been.calledWithExactly({
      TableName: config.resourceNames.subscriptionsStore,
      Key: {
        [subscriptionsStoreAttributes.partitionKey]: foundSubscription.typeName,
        [subscriptionsStoreAttributes.sortKey]: foundSubscription.connectionID_subscriptionID,
      },
    })
  })
})

describe('The "deleteAllSubscription" method', () => {
  let dbQueryStub: SinonStub
  let dbBatchWriteFake: SinonStub
  let db: DynamoDB.DocumentClient
  let connectionID: string
  let queryArguments: DocumentClient.QueryInput

  beforeEach(() => {
    dbQueryStub = stub()
    dbQueryStub.throws('db.query called with wrong arguments')
    dbBatchWriteFake = stub().returns({ promise: () => Promise.resolve() })
    db = createStubInstance(DynamoDB.DocumentClient, {
      query: dbQueryStub as any,
      batchWrite: dbBatchWriteFake as any,
    })
    connectionID = random.uuid()
    queryArguments = {
      TableName: config.resourceNames.subscriptionsStore,
      IndexName: subscriptionsStoreAttributes.indexByConnectionIDName(config),
      KeyConditionExpression: `${subscriptionsStoreAttributes.indexByConnectionIDPartitionKey} = :partitionKey`,
      ExpressionAttributeValues: { ':partitionKey': connectionID },
    }
  })
  afterEach(() => {
    restore()
  })

  it('does not delete anything if there is no subscription to delete', async () => {
    dbQueryStub.withArgs(queryArguments).returns({
      promise: () => Promise.resolve({ Items: [] }),
    })

    await deleteAllSubscriptions(db, config, logger, connectionID)
    expect(db.batchWrite).not.to.have.been.called
  })

  it('deletes all the expected subscriptions', async () => {
    const subscriptionIDOne = random.uuid()
    const subscriptionIDTwo = random.uuid()
    const foundSubscriptions: Array<SubscriptionIndexRecord> = [
      {
        typeName: random.alphaNumeric(10),
        connectionID,
        subscriptionID: subscriptionIDOne,
        // eslint-disable-next-line @typescript-eslint/camelcase
        connectionID_subscriptionID: sortKeyForSubscription(connectionID, subscriptionIDOne),
      },
      {
        typeName: random.alphaNumeric(10),
        connectionID,
        subscriptionID: subscriptionIDTwo,
        // eslint-disable-next-line @typescript-eslint/camelcase
        connectionID_subscriptionID: sortKeyForSubscription(connectionID, subscriptionIDTwo),
      },
    ]
    dbQueryStub.withArgs(queryArguments).returns({
      promise: () =>
        Promise.resolve({
          Items: foundSubscriptions,
        }),
    })

    await deleteAllSubscriptions(db, config, logger, connectionID)

    expect(dbBatchWriteFake).to.have.been.calledWithExactly({
      RequestItems: {
        [config.resourceNames.subscriptionsStore]: foundSubscriptions.map((subscriptionRecord) => ({
          DeleteRequest: {
            Key: {
              [subscriptionsStoreAttributes.partitionKey]: subscriptionRecord.typeName,
              [subscriptionsStoreAttributes.sortKey]: subscriptionRecord.connectionID_subscriptionID,
            },
          },
        })),
      },
    })
  })
})
