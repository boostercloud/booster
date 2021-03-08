/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from '../expect'
import * as Library from '../../src/library/events-adapter'
import { restore, fake, match, createStubInstance } from 'sinon'
import { EventEnvelope, BoosterConfig, UUID, Logger } from '@boostercloud/framework-types'
import { DynamoDBStreamEvent } from 'aws-lambda'
import { DynamoDB } from 'aws-sdk'
import { eventsStoreAttributes } from '../../src'
import { partitionKeyForEvent } from '../../src/library/keys-helper'
import { DocumentClient, Converter } from 'aws-sdk/clients/dynamodb'

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
      const kinesisMessage = wrapEventEnvelopesForDynamoDB(expectedEnvelopes)

      const gotEnvelopes = Library.rawEventsToEnvelopes(kinesisMessage)

      expect(gotEnvelopes).to.be.deep.equal(expectedEnvelopes)
    })
  })

  describe('the `readEntityEventsSince` method', () => {
    it('queries the events table to find all events related to a specific entity', async () => {
      const dynamoDB = createStubInstance(DynamoDB.DocumentClient)
      dynamoDB.query = fake.returns({
        promise: fake.resolves({
          Items: [],
        }),
      }) as any
      const config = new BoosterConfig('test')
      config.appName = 'nuke-button'

      await Library.readEntityEventsSince(dynamoDB, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(dynamoDB.query).to.have.been.calledWith(
        match({
          TableName: 'nuke-button-app-events-store',
          ConsistentRead: true,
          KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey AND ${eventsStoreAttributes.sortKey} > :fromTime`,
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
          TableName: 'nuke-button-app-events-store',
          ConsistentRead: true,
          KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey`,
          ExpressionAttributeValues: {
            ':partitionKey': partitionKeyForEvent('SomeEntity', 'someSpecialID', 'snapshot'),
          },
          ScanIndexForward: false,
          Limit: 1,
        })
      )
    })
  })

  describe('the `storeEvents` method', () => {
    it('publishes the eventEnvelopes passed via parameter', async () => {
      const config = new BoosterConfig('test')
      config.appName = 'test-app'
      const requestID = 'request-id'
      const streamName = config.resourceNames.eventsStore
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

      const fakeBatchWrite = fake.returns({
        promise: fake.resolves(''),
      })
      const fakeDynamo: DocumentClient = { batchWrite: fakeBatchWrite } as any

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

      await Library.storeEvents(fakeDynamo, eventEnvelopes, config, fakeLogger)

      expect(fakeBatchWrite).to.be.calledWith(
        match({
          RequestItems: match.has(streamName, match.has('length', 2)),
        })
      )
    })

    it('publishes the eventEnvelopes passed via parameter in batch of 25 elements', async () => {
      const config = new BoosterConfig('test')
      config.appName = 'test-app'
      const makeEvent = (n: string): EventEnvelope => ({
        version: 1,
        entityID: 'id',
        kind: 'event',
        value: {
          id: n,
        },
        typeName: 'EventName',
        entityTypeName: 'EntityName',
        requestID: 'requestID',
        createdAt: 'once',
      })
      const eventEnvelopes = Array.from({ length: 51 }, (_, n) => makeEvent(n.toString()))

      const batches: Array<Array<string>> = []

      const fakeBatchWrite = fake((params: DynamoDB.DocumentClient.BatchWriteItemInput) => {
        const batch: Array<string> = []
        params.RequestItems[config.resourceNames.eventsStore].forEach((value) => {
          batch.push(value.PutRequest?.Item?.value?.id ?? '-1')
        })
        batches.push(batch)
        return {
          promise: fake.resolves(''),
        }
      })

      const fakeDynamo: DocumentClient = { batchWrite: fakeBatchWrite } as any

      await Library.storeEvents(fakeDynamo, eventEnvelopes, config, fakeLogger)

      for (const batch of batches) {
        // chai-arrays doesn't have typings, so using with `any`
        const expectBatch = expect(batch) as any
        expectBatch.to.be.sorted((prev: number, next: number) => prev < next)
      }
      expect(fakeBatchWrite).to.be.calledThrice
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

function wrapEventEnvelopesForDynamoDB(eventEnvelopes: Array<EventEnvelope>): DynamoDBStreamEvent {
  const dynamoMessage = {
    Records: eventEnvelopes.map((envelope) => ({
      dynamodb: {
        NewImage: Converter.marshall(envelope),
      },
    })),
  }
  return dynamoMessage as DynamoDBStreamEvent
}
