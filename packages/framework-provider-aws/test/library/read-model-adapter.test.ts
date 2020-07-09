/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { replace, fake } from 'sinon'
import { fetchReadModel, storeReadModel, rawReadModelEventsToEnvelopes } from '../../src/library/read-model-adapter'
import { DynamoDB } from 'aws-sdk'
import { BoosterConfig, Logger, ReadModelEnvelope } from '@boostercloud/framework-types'
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
  it("responds with an error when the read model doesn't exist", async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'get',
      fake.returns({
        promise: fake.rejects('not found'),
      })
    )

    await expect(fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')).to.be.eventually.rejectedWith(
      'not found'
    )

    expect(db.get).to.have.been.calledOnceWith({
      TableName: 'new-booster-app-app-SomeReadModel',
      Key: { id: 'someReadModelID' },
    })
  })

  it('responds with a read model when it exist', async () => {
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
      Key: { id: 'someReadModelID' },
    })
    expect(result).to.deep.equal({ some: 'object' })
  })
})

describe('the "storeReadModel" method', () => {
  it('saves a read model', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'put',
      fake.returns({
        promise: fake.resolves({
          $response: {},
        }),
      })
    )

    const something = await storeReadModel(db, config, logger, 'SomeReadModel', { id: 777, some: 'object' } as any)

    expect(db.put).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-app-SomeReadModel',
      Item: { id: 777, some: 'object' },
    })
    expect(something).not.to.be.null
  })
})

function generateReadModelTableARN(config: BoosterConfig, readModelName: string): string {
  return `arn:aws:dynamodb:eu-west-1:123456:table/${config.resourceNames.forReadModel(readModelName)}`
}
