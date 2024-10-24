/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import * as EventsAdapter from '../../src/library/events-adapter'
import { createStubInstance, fake, match, restore, SinonStubbedInstance, stub } from 'sinon'
import { BoosterConfig, EventEnvelope, UUID } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { eventsStoreAttributes } from '../../src/constants'
import { partitionKeyForEvent, partitionKeyForSnapshot } from '../../src/library/partition-keys'
import { Context } from '@azure/functions'
import { random } from 'faker'
import {
  addMockSystemGeneratedProperties,
  createMockEventEnvelopes,
  wrapEventEnvelopesForCosmosDB,
} from '../helpers/event-helper'

describe('Events adapter', () => {
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
      await EventsAdapter.readEntityEventsSince(mockCosmosDbClient as any, mockConfig, mockEntityName, mockEntityId)

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
            `AND c["${eventsStoreAttributes.sortKey}"] > @fromTime AND NOT IS_DEFINED(c["deletedAt"]) ORDER BY c["${eventsStoreAttributes.sortKey}"] ASC`,
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
      await EventsAdapter.readEntityLatestSnapshot(mockCosmosDbClient as any, mockConfig, mockEntityName, mockEntityId)

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
              value: partitionKeyForSnapshot(mockEntityName, mockEntityId),
            },
          ],
        })
      )
    })
  })

  describe('The "storeDispatchedEvent" method', () => {
    it('Persists the IDs of the eventEnvelopes passed via parameters', async () => {
      await EventsAdapter.storeDispatchedEvent(mockCosmosDbClient as any, mockEvents[0], mockConfig)

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(mockConfig.resourceNames.dispatchedEventsStore)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(mockConfig.resourceNames.dispatchedEventsStore).items.create
      ).to.have.been.calledWithExactly(match({ eventId: mockEvents[0].id }))
    })
  })
})
