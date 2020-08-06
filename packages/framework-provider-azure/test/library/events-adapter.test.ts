/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import * as EventsAdapter from '../../src/library/events-adapter'
import { createStubInstance, fake, restore, match } from 'sinon'
import { BoosterConfig, Logger, EventEnvelope } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import sinon = require('sinon')
import { eventsStoreAttributes } from '../../src/constants'
import { partitionKeyForEvent } from '../../src/library/partition-keys'
import { Context, ExecutionContext, TraceContext, Logger as AzureLogger } from '@azure/functions'

const fakeLogger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

const cosmosDb = createStubInstance(CosmosClient, {
  database: sinon.stub().returns({
    container: sinon.stub().returns({
      items: {
        query: sinon.stub().returns({
          fetchAll: fake.resolves({ resources: [] }) as any,
        }),
        create: sinon.stub().returns(fake.resolves({})),
      },
    }),
  }) as any,
})
const config = new BoosterConfig('test')

describe('the events-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawEventsToEnvelopes` method', () => {
    it('generates envelopes correctly from a Cosmos DB event', async () => {
      const expectedEnvelopes = buildEventEnvelopes().map((envelope: EventEnvelope) => {
        return {
          ...envelope,
          id: 'someId',
          _rid: 'something',
          _self: 'something',
          _etag: 'something',
          _attachments: 'something',
          _ts: 1234567890,
        }
      })
      const cosmosDbMessage: Context = wrapEventEnvelopesForCosmosDB(expectedEnvelopes)

      const gotEnvelopes = EventsAdapter.rawEventsToEnvelopes(cosmosDbMessage)

      expect(gotEnvelopes).to.deep.equal(expectedEnvelopes)
    })
  })

  describe('the "readEntityEventsSince" method', () => {
    it('queries the events table to find all events related to a specific entity', async () => {
      await EventsAdapter.readEntityEventsSince(cosmosDb as any, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
      expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
        config.resourceNames.eventsStore
      )
      expect(
        cosmosDb.database(config.resourceNames.applicationStack).container(config.resourceNames.eventsStore).items.query
      ).to.have.been.calledWithExactly(
        match({
          query: `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey AND c["${eventsStoreAttributes.sortKey}"] > @fromTime ORDER BY c["${eventsStoreAttributes.sortKey}"] DESC`,
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

  describe('the "readEntityLatestSnapshot" method', () => {
    it('finds the latest entity snapshot', async () => {
      await EventsAdapter.readEntityLatestSnapshot(cosmosDb as any, config, fakeLogger, 'SomeEntity', 'someSpecialID')

      expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
      expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
        config.resourceNames.eventsStore
      )
      expect(
        cosmosDb.database(config.resourceNames.applicationStack).container(config.resourceNames.eventsStore).items.query
      ).to.have.been.calledWithExactly(
        match({
          query: `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey ORDER BY c["${eventsStoreAttributes.sortKey}"] DESC OFFSET 0 LIMIT 1`,
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

  describe('the "storeEvents" method', () => {
    it('publishes the eventEnvelopes passed via parameter', async () => {
      await EventsAdapter.storeEvents(
        cosmosDb as any,
        [
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
        ],
        config,
        fakeLogger
      )

      expect(cosmosDb.database).to.have.been.calledWithExactly(config.resourceNames.applicationStack)
      expect(cosmosDb.database(config.resourceNames.applicationStack).container).to.have.been.calledWithExactly(
        config.resourceNames.eventsStore
      )
      expect(
        cosmosDb.database(config.resourceNames.applicationStack).container(config.resourceNames.eventsStore).items
          .create
      ).to.have.been.calledWithExactly(
        match({
          version: 1,
          entityID: 'id',
          kind: 'event',
          value: {
            id: 'id',
          },
          typeName: 'EventName',
          entityTypeName: 'EntityName',
          requestID: 'requestID',
          [eventsStoreAttributes.partitionKey]: partitionKeyForEvent('EntityName', 'id', 'event'),
          [eventsStoreAttributes.sortKey]: match.defined,
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

function wrapEventEnvelopesForCosmosDB(eventEnvelopes: Array<EventEnvelope>): Context {
  return {
    bindingData: {},
    bindingDefinitions: [],
    executionContext: {} as ExecutionContext,
    invocationId: '',
    log: {} as AzureLogger,
    traceContext: {} as TraceContext,
    done(err?: Error | string | null, result?: any): void {},
    bindings: {
      rawEvent: eventEnvelopes.map((eventEnvelope: EventEnvelope): any => {
        return {
          ...eventEnvelope,
          id: 'someId',
          _rid: 'something',
          _self: 'something',
          _etag: 'something',
          _attachments: 'something',
          _ts: 1234567890,
        }
      }),
    },
  }
}
