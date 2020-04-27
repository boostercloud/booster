import * as chai from 'chai'
import { expect } from 'chai'
import { fake } from 'sinon'
import { DynamoDBStreamEvent } from 'aws-lambda'
import { rawReadModelEventsToEnvelopes } from '../../src/library/subscription-adapter'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { ReadModelEnvelope } from '@boostercloud/framework-types/dist'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

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
          eventSourceARN: testARNForReadModelName(config, 'CartReadModel'),
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
          eventSourceARN: testARNForReadModelName(config, 'CartReadModel'),
          dynamodb: { NewImage: { id: { S: 'test' } } },
        },
        {
          // An event with missing required fields
          eventSourceARN: testARNForReadModelName(config, 'CartReadModel'),
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
          eventSourceARN: testARNForReadModelName(config, 'ReadModelOne'),
          dynamodb: {
            NewImage: {
              id: { S: expectedReadModelOne.value.id.toString() },
              aField: { N: `${expectedReadModelOne.value.aField}` },
            },
          },
        },
        {
          eventSourceARN: testARNForReadModelName(config, 'ReadModelTwo'),
          dynamodb: {
            NewImage: {
              id: { S: expectedReadModelTwo.value.id.toString() },
              aField: { N: `${expectedReadModelTwo.value.aField}` },
            },
          },
        },
      ],
    }
    await expect(rawReadModelEventsToEnvelopes(config, logger, events)).to.be.eventually.become([
      expectedReadModelOne,
      expectedReadModelTwo,
    ])
  })
})

function testARNForReadModelName(config: BoosterConfig, readModelName: string): string {
  return `arn:aws:dynamodb:eu-west-1:123456:table/${config.resourceNames.forReadModel(readModelName)}`
}
