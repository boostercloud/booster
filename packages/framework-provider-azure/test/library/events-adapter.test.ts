/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import * as EventsAdapter from '../../src/library/events-adapter'
import { createStubInstance, fake, restore, match, stub, SinonStubbedInstance } from 'sinon'
import { BoosterConfig, EventEnvelope, Logger, UUID } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { eventsStoreAttributes } from '../../src/constants'
import { partitionKeyForEvent } from '../../src/library/partition-keys'
import { Context } from '@azure/functions'
import { random } from 'faker'
import {
  addMockSystemGeneratedProperties,
  createMockEventEnvelopes,
  wrapEventEnvelopesForCosmosDB,
} from '../helpers/event-helper'

describe('Events adapter', () => {
  let mockLogger: Logger
  let mockConfig: BoosterConfig
  let mockEvents: Array<EventEnvelope>

  let mockEntityName: string
  let mockEntityId: UUID

  let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>

  beforeEach(() => {
    mockCosmosDbClient = createStubInstance(CosmosClient, {
      database: stub().returns({
        container: stub().returns({
          items: {
            query: stub().returns({
              fetchAll: fake.resolves({ resources: [] }) as any,
            }),
            create: stub().returns(fake.resolves({})),
          },
        }),
      }) as any,
    })
    mockConfig = new BoosterConfig('test')
    mockLogger = {
      info: fake(),
      error: fake(),
      debug: fake(),
    }
    mockEntityName = random.word()
    mockEntityId = random.uuid()
    mockEvents = createMockEventEnvelopes(2)
  })

  afterEach(() => {
    restore()
  })

  describe('The "rawEventsToEnvelopes" method', () => {
    it('Generates envelopes correctly from a Cosmos DB event', async () => {
      const expectedEnvelopes = addMockSystemGeneratedProperties(mockEvents)
      const cosmosDbMessage: Context = wrapEventEnvelopesForCosmosDB(expectedEnvelopes)

      const gotEnvelopes = EventsAdapter.rawEventsToEnvelopes(cosmosDbMessage)

      expect(gotEnvelopes).to.deep.equal(expectedEnvelopes)
    })
  })

  describe('The "readEntityEventsSince" method', () => {
    it('Queries the events table to find all events related to a specific entity', async () => {
      await EventsAdapter.readEntityEventsSince(
        mockCosmosDbClient as any,
        mockConfig,
        mockLogger,
        mockEntityName,
        mockEntityId
      )

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(mockConfig.resourceNames.eventsStore).items.query
      ).to.have.been.calledWithExactly(
        match({
          query:
            `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
            `AND c["${eventsStoreAttributes.sortKey}"] > @fromTime ORDER BY c["${eventsStoreAttributes.sortKey}"] DESC`,
          parameters: [
            {
              name: '@partitionKey',
              value: partitionKeyForEvent(mockEntityName, mockEntityId),
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

  describe('The "readEntityLatestSnapshot" method', () => {
    it('Finds the latest entity snapshot', async () => {
      await EventsAdapter.readEntityLatestSnapshot(
        mockCosmosDbClient as any,
        mockConfig,
        mockLogger,
        mockEntityName,
        mockEntityId
      )

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(mockConfig.resourceNames.eventsStore).items.query
      ).to.have.been.calledWithExactly(
        match({
          query:
            `SELECT * FROM c WHERE c["${eventsStoreAttributes.partitionKey}"] = @partitionKey ` +
            `ORDER BY c["${eventsStoreAttributes.sortKey}"] DESC OFFSET 0 LIMIT 1`,
          parameters: [
            {
              name: '@partitionKey',
              value: partitionKeyForEvent(mockEntityName, mockEntityId, 'snapshot'),
            },
          ],
        })
      )
    })
  })

  describe('The "storeEvents" method', () => {
    it('Publishes the eventEnvelopes passed via parameter', async () => {
      await EventsAdapter.storeEvents(mockCosmosDbClient as any, [mockEvents[0]], mockConfig, mockLogger)

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(mockConfig.resourceNames.eventsStore).items.create
      ).to.have.been.calledWithExactly(
        match({
          ...mockEvents[0],
          [eventsStoreAttributes.partitionKey]: partitionKeyForEvent(
            mockEvents[0].entityTypeName,
            mockEvents[0].entityID,
            mockEvents[0].kind
          ),
          [eventsStoreAttributes.sortKey]: match.defined,
        })
      )
    })
  })
})
