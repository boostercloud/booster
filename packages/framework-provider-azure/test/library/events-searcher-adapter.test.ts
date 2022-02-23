/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, fake, restore, stub, SinonStubbedInstance } from 'sinon'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, EventFilter, Logger } from '@boostercloud/framework-types'
import { searchEvents } from '../../src/library/events-searcher-adapter'
import * as searchModule from '../../src/helpers/query-helper'

describe('Events Searcher adapter', () => {
  describe('The "searchEvents" method', () => {
    let mockLogger: Logger
    let mockConfig: BoosterConfig

    let mockCosmosDbClient: SinonStubbedInstance<CosmosClient>

    beforeEach(() => {
      mockConfig = new BoosterConfig('test')
      mockLogger = {
        info: fake(),
        error: fake(),
        debug: fake(),
      }
      mockCosmosDbClient = createStubInstance(CosmosClient, {
        database: stub().returns({
          container: stub().returns({
            items: {
              query: stub().returns({
                fetchAll: fake.resolves({ resources: [] }) as any,
              }),
            },
          }),
        }) as any,
      })
    })

    afterEach(() => {
      restore()
    })

    it('Generate filters for entity, entityId and time when EventFilter has all fields', async () => {
      const filters: EventFilter = {
        from: 'from',
        to: 'to',
        entity: 'entity',
        entityID: 'entityID',
        type: 'type',
      }
      const mockSearch = stub(searchModule, 'search').returns(Promise.resolve([]))
      const eventStoreName = 'new-booster-app-app-events-store'
      await searchEvents(mockCosmosDbClient as any, mockConfig, mockLogger, filters)

      expect(mockSearch).to.have.been.calledWithExactly(
        mockCosmosDbClient,
        mockConfig,
        mockLogger,
        eventStoreName,
        {
          entityTypeName_entityID_kind: { eq: 'entity-entityID-event' },
          createdAt: { gte: 'from', lte: 'to' },
          kind: { eq: 'event' },
        },
        undefined,
        undefined,
        undefined,
        {
          createdAt: 'DESC',
        }
      )
    })

    it('Generate filters for entity, entityId when EventFilter has entity and entityID fields', async () => {
      const filters: EventFilter = {
        entity: 'entity',
        entityID: 'entityID',
      }
      const mockSearch = stub(searchModule, 'search').returns(Promise.resolve([]))
      const eventStoreName = 'new-booster-app-app-events-store'
      await searchEvents(mockCosmosDbClient as any, mockConfig, mockLogger, filters)

      expect(mockSearch).to.have.been.calledWithExactly(
        mockCosmosDbClient,
        mockConfig,
        mockLogger,
        eventStoreName,
        {
          entityTypeName_entityID_kind: { eq: 'entity-entityID-event' },
          kind: { eq: 'event' },
        },
        undefined,
        undefined,
        undefined,
        {
          createdAt: 'DESC',
        }
      )
    })

    it('Generate filters for type when EventFilter has type field', async () => {
      const filters: EventFilter = {
        type: 'type',
      }
      const mockSearch = stub(searchModule, 'search').returns(Promise.resolve([]))
      const eventStoreName = 'new-booster-app-app-events-store'
      await searchEvents(mockCosmosDbClient as any, mockConfig, mockLogger, filters)

      expect(mockSearch).to.have.been.calledWithExactly(
        mockCosmosDbClient,
        mockConfig,
        mockLogger,
        eventStoreName,
        {
          typeName: { eq: 'type' },
          kind: { eq: 'event' },
        },
        undefined,
        undefined,
        undefined,
        {
          createdAt: 'DESC',
        }
      )
    })

    it('Generate filters for entity when EventFilter has only entity field', async () => {
      const filters: EventFilter = {
        entity: 'entity',
      }
      const mockSearch = stub(searchModule, 'search').returns(Promise.resolve([]))
      const eventStoreName = 'new-booster-app-app-events-store'
      await searchEvents(mockCosmosDbClient as any, mockConfig, mockLogger, filters)

      expect(mockSearch).to.have.been.calledWithExactly(
        mockCosmosDbClient,
        mockConfig,
        mockLogger,
        eventStoreName,
        {
          entityTypeName: { eq: 'entity' },
          kind: { eq: 'event' },
        },
        undefined,
        undefined,
        undefined,
        {
          createdAt: 'DESC',
        }
      )
    })
  })
})
