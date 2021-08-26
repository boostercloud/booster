/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { replace, fake, restore } from 'sinon'
import {
  fetchReadModel,
  storeReadModel,
  rawReadModelEventsToEnvelopes,
  deleteReadModel,
} from '../../src/library/read-models-adapter'
import { DynamoDB } from 'aws-sdk'
import {
  BoosterConfig,
  Logger,
  OptimisticConcurrencyUnexpectedVersionError,
  ReadModelEnvelope,
} from '@boostercloud/framework-types'
import { DynamoDBStreamEvent } from 'aws-lambda'

const logger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}
describe('the "rawReadModelEventsToEnvelopes" method', () => {
  const config = new BoosterConfig('test')
  config.appName = 'test-app'

  it('fails when some event does not have the required field "eventSourceARN"', async () => {
    const events: DynamoDBStreamEvent = {
      Records: [
        {
          // A well formed event
          eventSourceARN: generateReadModelTableARN(config, 'CartReadModel'),
          dynamodb: { NewImage: { id: { S: 'test' } } },
        },
        {
          // An event with missing required fields
          eventSourceARN: undefined, // Just make it explicit
          dynamodb: { NewImage: { id: { S: 'test' } } },
        },
      ],
    }
    await expect(rawReadModelEventsToEnvelopes(config, logger, events)).to.be.eventually.rejectedWith(
      /Received a DynamoDB stream event without/
    )
  })

  it('fails when some event does not have the required field "NewImage"', async () => {
    const events: DynamoDBStreamEvent = {
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
    }
    await expect(rawReadModelEventsToEnvelopes(config, logger, events)).to.be.eventually.rejectedWith(
      /Received a DynamoDB stream event without/
    )
  })

  it('returns the envelopes correctly', async () => {
    const expectedReadModelOne: ReadModelEnvelope = {
      typeName: 'ReadModelOne',
      value: {
        id: 'one',
        aField: 123,
      },
    }
    const expectedReadModelTwo: ReadModelEnvelope = {
      typeName: 'ReadModelTwo',
      value: {
        id: 'two',
        aField: 456,
      },
    }

    const events: DynamoDBStreamEvent = {
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
    }
    await expect(rawReadModelEventsToEnvelopes(config, logger, events)).to.eventually.become([
      expectedReadModelOne,
      expectedReadModelTwo,
    ])
  })
})

describe('the "fetchReadModel" method', () => {
  context("when the read model doesn't exist", () => {
    context('when no sequenceMetadata is defined', () => {
      it('responds with an error querying by partition key', async () => {
        const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
        const config = new BoosterConfig('test')
        replace(
          db,
          'get',
          fake.returns({
            promise: fake.rejects('not found'),
          })
        )

        await expect(
          fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')
        ).to.be.eventually.rejectedWith('not found')

        expect(db.get).to.have.been.calledOnceWith({
          TableName: 'new-booster-app-app-SomeReadModel',
          ConsistentRead: true,
          Key: { id: 'someReadModelID' },
        })
      })
    })

    context('when sequenceMetadata is defined', () => {
      it('responds with an error querying both by partition and sort keys', async () => {
        const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
        const config = new BoosterConfig('test')
        replace(
          db,
          'get',
          fake.returns({
            promise: fake.rejects('not found'),
          })
        )

        await expect(
          fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID', { name: 'asdf', value: '42' })
        ).to.be.eventually.rejectedWith('not found')

        expect(db.get).to.have.been.calledOnceWith({
          TableName: 'new-booster-app-app-SomeReadModel',
          ConsistentRead: true,
          Key: { id: 'someReadModelID', asdf: '42' },
        })
      })
    })
  })

  context('when the read model exists', () => {
    context('when no sequenceMetadata is defined', () => {
      it('gets the read model by partition key and responds with a read model', async () => {
        const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
        const config = new BoosterConfig('test')
        replace(
          db,
          'get',
          fake.returns({
            promise: fake.resolves({ Item: { some: 'object' } }),
          })
        )

        const result = await fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')

        expect(db.get).to.have.been.calledOnceWithExactly({
          TableName: 'new-booster-app-app-SomeReadModel',
          ConsistentRead: true,
          Key: { id: 'someReadModelID' },
        })
        expect(result).to.deep.equal({ some: 'object' })
      })
    })

    context('when sequenceMetadata is defined', () => {
      it('gets the read model by partition and sort key and responds with a read model', async () => {
        const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
        const config = new BoosterConfig('test')
        replace(
          db,
          'get',
          fake.returns({
            promise: fake.resolves({ Item: { some: 'object', time: '42' } }),
          })
        )

        const result = await fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID', {
          name: 'time',
          value: '42',
        })

        expect(db.get).to.have.been.calledOnceWithExactly({
          TableName: 'new-booster-app-app-SomeReadModel',
          ConsistentRead: true,
          Key: { id: 'someReadModelID', time: '42' },
        })
        expect(result).to.deep.equal({ some: 'object', time: '42' })
      })
    })
  })
})

describe('the "storeReadModel" method', () => {
  const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
  const config = new BoosterConfig('test')
  beforeEach(() => {
    restore()
  })

  it('saves a read model', async () => {
    replace(
      db,
      'put',
      fake.returns({
        promise: fake.resolves({
          $response: {},
        }),
      })
    )

    const something = await storeReadModel(db, config, logger, 'SomeReadModel', { id: 777, some: 'object' } as any, 0)

    expect(db.put).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-app-SomeReadModel',
      Item: { id: 777, some: 'object' },
      ConditionExpression: 'attribute_not_exists(boosterMetadata.version) OR boosterMetadata.version = :version',
      ExpressionAttributeValues: { ':version': 0 },
    })
    expect(something).not.to.be.null
  })

  it('throws the OptimisticConcurrencyUnexpectedVersionError when there is a ConditionalCheckFailedException', async () => {
    replace(
      db,
      'put',
      fake.returns({
        promise: () => {
          const e = new Error('test error')
          e.name = 'ConditionalCheckFailedException'
          throw e
        },
      })
    )

    await expect(
      storeReadModel(db, config, logger, 'SomeReadModel', { id: 777, some: 'object' } as any, 0)
    ).to.eventually.be.rejectedWith(OptimisticConcurrencyUnexpectedVersionError)
  })
})

describe('the "deleteReadModel"', () => {
  context('when the read model is not sequenced', () => {
    it('deletes an existing read model by the partition key', async () => {
      const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
      const config = new BoosterConfig('test')
      replace(
        db,
        'delete',
        fake.returns({
          promise: fake.resolves({
            $response: {},
          }),
        })
      )

      await deleteReadModel(db, config, logger, 'SomeReadModel', { id: 777, some: 'object' } as any)

      expect(db.delete).to.have.been.calledOnceWithExactly({
        TableName: 'new-booster-app-app-SomeReadModel',
        Key: { id: 777 },
      })
    })
  })

  context('when the read model is sequenced', () => {
    it('deletes an existing read model by both the partition and the sort keys', async () => {
      const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
      const config = new BoosterConfig('test')
      const readModelName = 'SomeReadModel'
      config.readModelSequenceKeys[readModelName] = 'time'
      replace(
        db,
        'delete',
        fake.returns({
          promise: fake.resolves({
            $response: {},
          }),
        })
      )

      await deleteReadModel(db, config, logger, readModelName, { id: '777', time: '42', some: 'object' } as any)

      expect(db.delete).to.have.been.calledOnceWithExactly({
        TableName: 'new-booster-app-app-SomeReadModel',
        Key: { id: '777', time: '42' },
      })
    })
  })
})

function generateReadModelTableARN(config: BoosterConfig, readModelName: string): string {
  return `arn:aws:dynamodb:eu-west-1:123456:table/${config.resourceNames.forReadModel(readModelName)}`
}
