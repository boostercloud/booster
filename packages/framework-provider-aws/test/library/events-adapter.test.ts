/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from 'chai'
import * as chai from 'chai'
import * as Library from '../../src/library/events-adapter'
import { restore, fake, match } from 'sinon'
import { EventEnvelope, BoosterConfig, UUID, Logger } from '@boostercloud/framework-types'
import { KinesisStreamEvent } from 'aws-lambda'
import { Kinesis } from 'aws-sdk'
import { createStubInstance } from 'sinon'
import { DynamoDB } from 'aws-sdk'
import { eventStorePartitionKeyAttribute, eventStoreSortKeyAttribute } from '../../src/constants'
import { partitionKeyForEvent } from '../../src/library/partition-keys'

chai.use(require('sinon-chai'))

const fakeLogger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

describe('the events-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawEventsToEnvelopes` method', () => {
    it('generates envelopes correctly from an AWS Kinesis event', async () => {
      const expectedEnvelopes = buildEventEnvelopes()
      const kinesisMessage = wrapEventEnvelopesForKinesis(expectedEnvelopes)

      const gotEnvelopes = Library.rawEventsToEnvelopes(kinesisMessage)

      expect(gotEnvelopes).to.be.deep.equal(expectedEnvelopes)
    })
  })

  describe('the `storeEvent` method', () => {
    it('stores an eventEnvelope in the corresponding DynamoDB database', async () => {
      const dynamoDB = createStubInstance(DynamoDB.DocumentClient)
      dynamoDB.put = fake.returns({ promise: fake.resolves('') }) as any
      const config = new BoosterConfig('test')
      config.appName = 'nuke-button'

      const eventEnvelope: EventEnvelope = {
        version: 1,
        entityID: 'id',
        kind: 'event',
        value: {
          id: 'id',
        },
        typeName: 'EventName',
        entityTypeName: 'EntityName',
        requestID: 'requestID',
        createdAt: 'once',
      }

      await Library.storeEvent(dynamoDB, config, fakeLogger, eventEnvelope)

      expect(dynamoDB.put).to.have.been.calledOnce
      expect(dynamoDB.put).to.have.been.calledWith(
        match({
          TableName: 'nuke-button-application-stack-events-store',
          Item: {
            ...eventEnvelope,
            [eventStorePartitionKeyAttribute]: partitionKeyForEvent(
              eventEnvelope.entityTypeName,
              eventEnvelope.entityID,
              eventEnvelope.kind
            ),
            [eventStoreSortKeyAttribute]: match.defined,
          },
        })
      )
    })
  })

  describe('the `readEntityEventsSince` method', () => {
    it('queries the events table to find all events related to a specific entity', async () => {
      const dynamoDB = createStubInstance(DynamoDB.DocumentClient)
      dynamoDB.query = fake.returns({ promise: fake.resolves('') }) as any
      const config = new BoosterConfig('test')
      config.appName = 'nuke-button'

      await Library.readEntityEventsSince(dynamoDB, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(dynamoDB.query).to.have.been.calledWith(
        match({
          TableName: 'nuke-button-application-stack-events-store',
          ConsistentRead: true,
          KeyConditionExpression: `${eventStorePartitionKeyAttribute} = :partitionKey AND ${eventStoreSortKeyAttribute} > :fromTime`,
          ExpressionAttributeValues: {
            ':partitionKey': partitionKeyForEvent('SomeEntity', 'someSpecialID'),
            ':fromTime': match.defined,
          },
          ScanIndexForward: true,
        })
      )
    })
  })

  describe('the `readEntityLatestSnapshot` method', () => {
    it('finds the latest entity snapshot', async () => {
      const dynamoDB = createStubInstance(DynamoDB.DocumentClient)
      dynamoDB.query = fake.returns({ promise: fake.resolves('') }) as any
      const config = new BoosterConfig('test')
      config.appName = 'nuke-button'

      await Library.readEntityLatestSnapshot(dynamoDB, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(dynamoDB.query).to.have.been.calledWith(
        match({
          TableName: 'nuke-button-application-stack-events-store',
          ConsistentRead: true,
          KeyConditionExpression: `${eventStorePartitionKeyAttribute} = :partitionKey`,
          ExpressionAttributeValues: {
            ':partitionKey': partitionKeyForEvent('SomeEntity', 'someSpecialID', 'snapshot'),
          },
          ScanIndexForward: false,
          Limit: 1,
        })
      )
    })
  })

  describe('the `publishEvents` method', () => {
    it('publishes the eventEnvelopes passed via parameter', async () => {
      const config = new BoosterConfig('test')
      config.appName = 'test-app'
      const requestID = 'request-id'
      const streamName = config.resourceNames.eventsStream
      const events = [
        {
          entityID(): UUID {
            return '123'
          },
        },
        {
          entityID(): UUID {
            return '456'
          },
        },
      ]

      const fakePutRecords = fake.returns({
        promise: fake.resolves(''),
      })
      const fakeKinesis: Kinesis = { putRecords: fakePutRecords } as any

      const eventEnvelopes = events.map(
        (e): EventEnvelope => {
          return {
            version: 1,
            kind: 'event',
            requestID,
            entityID: e.entityID(),
            entityTypeName: 'fake-entity-name',
            typeName: 'fake-type-name',
            value: {
              entityID: e.entityID,
            },
            createdAt: new Date().toISOString(),
          }
        }
      )

      await Library.publishEvents(fakeKinesis, eventEnvelopes, config, fakeLogger)

      expect(fakePutRecords).to.be.calledWith(
        match({
          StreamName: streamName,
          Records: match.has('length', 2),
        })
      )
    })
  })
})

function buildEventEnvelopes(): Array<EventEnvelope> {
  return [
    {
      version: 1,
      entityID: 'id',
      kind: 'event',
      value: {
        id: 'id',
      },
      typeName: 'EventName',
      entityTypeName: 'EntityName',
      requestID: 'requestID',
      createdAt: 'once',
    },
    {
      version: 1,
      entityID: 'id2',
      kind: 'event',
      value: {
        id: 'id2',
      },
      typeName: 'EventName2',
      entityTypeName: 'EntityName2',
      requestID: 'requestID2',
      createdAt: 'once upon a time',
    },
  ]
}

function wrapEventEnvelopesForKinesis(eventEnvelopes: Array<EventEnvelope>): KinesisStreamEvent {
  const kinesisMessage = {
    Records: eventEnvelopes.map((envelope) => {
      return {
        kinesis: {
          data: Buffer.from(JSON.stringify(envelope)).toString('base64'),
        },
      }
    }),
  }
  return kinesisMessage as KinesisStreamEvent
}
