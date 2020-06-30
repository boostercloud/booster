/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import * as Library from '../../src/library/events-adapter'
import { createStubInstance, fake, restore, match } from 'sinon'
import { EventEnvelope } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import sinon = require('sinon')
import { eventStorePartitionKeyAttribute, eventStoreSortKeyAttribute } from '../../src'
import { partitionKeyForEvent } from '../../src/library/partition-keys'

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
    xit('generates envelopes correctly from a Cosmos DB event', async () => {
      const expectedEnvelopes = buildEventEnvelopes()
      const cosmosDbMessage = wrapEventEnvelopesForCosmosDB(expectedEnvelopes)

      const gotEnvelopes = Library.rawEventsToEnvelopes(cosmosDbMessage)

      expect(gotEnvelopes).to.be.deep.equal(expectedEnvelopes)
    })
  })

  describe('the `readEntityEventsSince` method', () => {
    it('queries the events table to find all events related to a specific entity', async () => {
      const cosmosDb = createStubInstance(CosmosClient, {
        // @ts-ignore
        database: sinon.stub().returns({
          container: sinon.stub().returns({
            items: {
              query: sinon.stub().returns({
                fetchAll: fake.resolves({ resources: [] }) as any,
              }),
            },
          }),
        }),
      })
      const config = new BoosterConfig('test')

      await Library.readEntityEventsSince(cosmosDb, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(cosmosDb.database().container().items.query).to.have.been.calledWith(
        match({
          query: `SELECT * FROM c where c.${eventStorePartitionKeyAttribute} = @partitionKey AND c.${eventStoreSortKeyAttribute} > @fromTime ORDER BY c.${eventStoreSortKeyAttribute} DESC`,
          parameters: [
            {
              name: '@partitionKey',
              value: partitionKeyForEvent('SomeEntity', 'someSpecialID'),
            },
            {
              name: '@fromTime',
              value: match.defined,
            },
          ],
        })
      )
    })
  })

  describe('the `readEntityLatestSnapshot` method', () => {
    it('finds the latest entity snapshot', async () => {
      const cosmosDb = createStubInstance(CosmosClient, {
        // @ts-ignore
        database: sinon.stub().returns({
          container: sinon.stub().returns({
            items: {
              query: sinon.stub().returns({
                fetchAll: fake.resolves({ resources: [] }) as any,
              }),
            },
          }),
        }),
      })
      const config = new BoosterConfig('test')

      await Library.readEntityLatestSnapshot(cosmosDb, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(cosmosDb.database().container().items.query).to.have.been.calledWith(
        match({
          query: `SELECT * FROM c where c.${eventStorePartitionKeyAttribute} = @partitionKey ORDER BY c.${eventStoreSortKeyAttribute} DESC LIMIT 1`,
          parameters: [
            {
              name: '@partitionKey',
              value: partitionKeyForEvent('SomeEntity', 'someSpecialID', 'snapshot'),
            },
          ],
        })
      )
    })
  })

  describe('the `storeEvents` method', () => {
    it('publishes the eventEnvelopes passed via parameter', async () => {
      // @TODO
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

function wrapEventEnvelopesForCosmosDB(eventEnvelopes: Array<EventEnvelope>): Array<any> {
  return eventEnvelopes.map((eventEnvelope: EventEnvelope): any => {
    return {
      ...eventEnvelope,
      id: '22e01861-a05d-422d-a1f6-9b909c707fda',
      _rid: 'lCx+AMlTPrICAAAAAAAAAA==',
      _self: 'dbs/lCx+AA==/colls/lCx+AMlTPrI=/docs/lCx+AMlTPrICAAAAAAAAAA==/',
      _etag: '"00004d01-0000-0100-0000-5ef3d26c0000"',
      _attachments: 'attachments/',
      _ts: 1593037420,
    }
  })
}
