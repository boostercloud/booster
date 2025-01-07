/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import * as EventsStoreAdapter from '../../src/library/events-store-adapter'
import { createStubInstance, fake, match, restore, SinonStubbedInstance, stub } from 'sinon'
import { BoosterConfig, EventEnvelope } from '@boostercloud/framework-types'
import { CosmosClient } from '@azure/cosmos'
import { eventsStoreAttributes } from '../../src/constants'
import { partitionKeyForEvent } from '../../src/library/partition-keys'
import { createMockEventEnvelopes } from '../helpers/event-helper'

describe('Events store adapter', () => {
  let mockConfig: BoosterConfig
  let mockEvents: Array<EventEnvelope>

  let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>

  beforeEach(() => {
    mockCosmosDbClient = createStubInstance(CosmosClient, {
      database: stub().returns({
        container: stub().returns({
          items: {
            batch: fake.resolves({ code: 200 }) as any,
            create: stub().returns(fake.resolves({})),
          },
        }),
      }) as any,
    })
    mockConfig = new BoosterConfig('test')
    mockEvents = createMockEventEnvelopes(2)
  })

  afterEach(() => {
    restore()
  })

  describe('The "storeEvents" method', () => {
    it('Publishes the eventEnvelopes passed via parameter in batches', async () => {
      await EventsStoreAdapter.storeEvents(mockCosmosDbClient as any, [mockEvents[0]], mockConfig)

      expect(mockCosmosDbClient.database).to.have.been.calledWithExactly(mockConfig.resourceNames.applicationStack)
      expect(
        mockCosmosDbClient.database(mockConfig.resourceNames.applicationStack).container
      ).to.have.been.calledWithExactly(mockConfig.resourceNames.eventsStore)
      expect(
        mockCosmosDbClient
          .database(mockConfig.resourceNames.applicationStack)
          .container(mockConfig.resourceNames.eventsStore).items.batch
      ).to.have.been.calledWithExactly(
        [
          {
            operationType: 'Create',
            resourceBody: {
              ...mockEvents[0],
              [eventsStoreAttributes.partitionKey]: partitionKeyForEvent(
                mockEvents[0].entityTypeName,
                mockEvents[0].entityID
              ),
              [eventsStoreAttributes.sortKey]: match.defined,
            },
          },
        ],
        partitionKeyForEvent(mockEvents[0].entityTypeName, mockEvents[0].entityID),
        {}
      )
    })

    it('Publishes the eventEnvelopes one by one if batching is disabled', async () => {
      mockConfig.azureConfiguration.enableEventBatching = false

      await EventsStoreAdapter.storeEvents(mockCosmosDbClient as any, [mockEvents[0]], mockConfig)
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
            mockEvents[0].entityID
          ),
          [eventsStoreAttributes.sortKey]: match.defined,
        })
      )
    })
  })
})
