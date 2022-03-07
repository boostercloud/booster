/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, fake, restore, stub, SinonStubbedInstance } from 'sinon'
import { CosmosClient } from '@azure/cosmos'
import { BoosterConfig, EventSearchParameters, Logger } from '@boostercloud/framework-types'
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

    it('Generate filters for entity, entityId and time when EventSearchParameters has all fields', async () => {
      const filters: EventSearchParameters = {
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

    it('Generate filters for entity, entityId and time when EventSearchParameters has all fields and limited', async () => {
      const filters: EventSearchParameters = {
        from: 'from',
        to: 'to',
        entity: 'entity',
        entityID: 'entityID',
        type: 'type',
        limit: 3,
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
        3,
        undefined,
        undefined,
        {
          createdAt: 'DESC',
        }
      )
    })

    it('Generate filters for entity, entityId when EventSearchParameters has entity and entityID fields', async () => {
      const filters: EventSearchParameters = {
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

    it('Generate filters for type when EventSearchParameters has type field', async () => {
      const filters: EventSearchParameters = {
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

    it('Generate filters for entity when EventSearchParameters has only entity field', async () => {
      const parameters: EventSearchParameters = {
        entity: 'entity',
      }
      const mockSearch = stub(searchModule, 'search').returns(Promise.resolve([]))
      const eventStoreName = 'new-booster-app-app-events-store'
      await searchEvents(mockCosmosDbClient as any, mockConfig, mockLogger, parameters)

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
