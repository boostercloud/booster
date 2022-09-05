/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, fake, replace, restore, SinonSpy, SinonStubbedInstance } from 'sinon'
import {
  BoosterConfig,
  EventEnvelope,
  EventParametersFilterByEntity,
  EventParametersFilterByType,
  EventSearchParameters,
  EventSearchResponse,
  PaginatedEventSearchResponse,
} from '@boostercloud/framework-types'
import { date, random } from 'faker'
import { DynamoDB } from 'aws-sdk'
import { PaginatedResult, searchEvents } from '../../src/library/events-searcher-adapter'
import { eventsStoreAttributes } from '../../src'
import { partitionKeyForEvent, partitionKeyForIndexByEntity } from '../../src/library/keys-helper'
import { DocumentClient } from 'aws-sdk/clients/dynamodb'
import rewire = require('rewire')

describe('Events searcher adapter', () => {
  const config: BoosterConfig = new BoosterConfig('test')
  let db: SinonStubbedInstance<DynamoDB.DocumentClient>
  beforeEach(() => {
    db = createStubInstance(DynamoDB.DocumentClient, {
      query: {
        promise: fake.returns({
          Items: [],
        }),
      } as any,
      batchGet: {
        promise: fake.returns({
          Responses: {},
        }),
      } as any,
    })
  })
  after(() => {
    restore()
  })

  describe('The "searchEvents" method', () => {
    it('throws an error when an invalid search is made', async () => {
      await expect(searchEvents(db, config, {} as never)).to.be.rejectedWith(/Invalid search event query/)
    })

    describe('for a search by entity with ID', () => {
      let filter: EventParametersFilterByEntity
      let entityID: string
      beforeEach(() => {
        entityID = random.uuid()
        filter = {
          entity: random.alpha(),
          entityID: entityID,
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => false,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            ConsistentRead: true,
            ScanIndexForward: false,
            Limit: undefined,
            KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: { ':partitionKey': partitionKeyForEvent(filter.entity, entityID) },
            ExclusiveStartKey: undefined,
          }
        }
      )
    })

    describe('for a paginated search by entity with ID', () => {
      let filter: EventParametersFilterByEntity
      let entityID: string
      beforeEach(() => {
        entityID = random.uuid()
        filter = {
          entity: random.alpha(),
          entityID: entityID,
          limit: 1,
          afterCursor: { id: '1' },
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => true,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            ConsistentRead: true,
            ScanIndexForward: false,
            Limit: 1,
            KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: { ':partitionKey': partitionKeyForEvent(filter.entity, entityID) },
            ExclusiveStartKey: { id: '1' },
          }
        }
      )
    })

    describe('for a search by entity and no ID', () => {
      let filter: EventParametersFilterByEntity
      beforeEach(() => {
        filter = {
          entity: random.alpha(),
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => false,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            IndexName: eventsStoreAttributes.indexByEntity.name(config),
            ScanIndexForward: false,
            Limit: undefined,
            KeyConditionExpression: `${eventsStoreAttributes.indexByEntity.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: {
              ':partitionKey': partitionKeyForIndexByEntity(filter.entity, 'event'),
            },
            ExclusiveStartKey: undefined,
          }
        },
        true
      )
    })

    describe('for a paginated search by entity and no ID', () => {
      let filter: EventParametersFilterByEntity
      beforeEach(() => {
        filter = {
          entity: random.alpha(),
          limit: 1,
          afterCursor: { id: '1' },
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => true,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            IndexName: eventsStoreAttributes.indexByEntity.name(config),
            ScanIndexForward: false,
            Limit: 1,
            KeyConditionExpression: `${eventsStoreAttributes.indexByEntity.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: {
              ':partitionKey': partitionKeyForIndexByEntity(filter.entity, 'event'),
            },
            ExclusiveStartKey: { id: '1' },
          }
        },
        true
      )
    })

    describe('for a search by type', () => {
      let filter: EventParametersFilterByType
      beforeEach(() => {
        filter = {
          type: random.alpha(),
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => false,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            IndexName: eventsStoreAttributes.indexByType.name(config),
            ScanIndexForward: false,
            Limit: undefined,
            KeyConditionExpression: `${eventsStoreAttributes.indexByType.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: {
              ':partitionKey': filter.type,
            },
            ExclusiveStartKey: undefined,
          }
        },
        true
      )
    })

    describe('for a paginated search by type', () => {
      let filter: EventParametersFilterByType
      beforeEach(() => {
        filter = {
          type: random.alpha(),
          limit: 1,
          afterCursor: { id: '1' },
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => true,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            IndexName: eventsStoreAttributes.indexByType.name(config),
            ScanIndexForward: false,
            Limit: 1,
            KeyConditionExpression: `${eventsStoreAttributes.indexByType.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: {
              ':partitionKey': filter.type,
            },
            ExclusiveStartKey: { id: '1' },
          }
        },
        true
      )
    })
  })

  function runQueryTestsWithTimeFiltersVariants(
    getFilters: () => EventSearchParameters,
    getPaginated: () => boolean,
    getQuery: () => DocumentClient.QueryInput,
    requiresExtraQueryToMainTable = false
  ): void {
    context('with no time filters', () => {
      it('does the query with no time filters', async () => {
        await searchEvents(db, config, getFilters(), getPaginated())
        expect(db.query).to.have.been.calledWithExactly(getQuery())
      })
    })

    context('with "from" time filter and limit', () => {
      let filterWithFrom: EventSearchParameters
      let queryWithFromTimeAdditions: DocumentClient.QueryInput
      beforeEach(() => {
        filterWithFrom = getFilters()
        filterWithFrom.from = date.recent().toISOString()
        filterWithFrom.limit = 3

        queryWithFromTimeAdditions = getQuery()
        queryWithFromTimeAdditions.KeyConditionExpression += ` AND ${eventsStoreAttributes.sortKey} >= :fromTime`
        queryWithFromTimeAdditions.ExpressionAttributeValues![':fromTime'] = filterWithFrom.from
        queryWithFromTimeAdditions.Limit = filterWithFrom.limit
      })

      it('does the query with "from" time filter and limit', async () => {
        await searchEvents(db, config, filterWithFrom, getPaginated())
        expect(db.query).to.have.been.calledWithExactly(queryWithFromTimeAdditions)
      })
    })

    context('with "from" time filter', () => {
      let filterWithFrom: EventSearchParameters
      let queryWithFromTimeAdditions: DocumentClient.QueryInput
      beforeEach(() => {
        filterWithFrom = getFilters()
        filterWithFrom.from = date.recent().toISOString()

        queryWithFromTimeAdditions = getQuery()
        queryWithFromTimeAdditions.KeyConditionExpression += ` AND ${eventsStoreAttributes.sortKey} >= :fromTime`
        queryWithFromTimeAdditions.ExpressionAttributeValues![':fromTime'] = filterWithFrom.from
      })

      it('does the query with "from" time filter', async () => {
        await searchEvents(db, config, filterWithFrom, getPaginated())
        expect(db.query).to.have.been.calledWithExactly(queryWithFromTimeAdditions)
      })
    })

    context('with "to" time filters', () => {
      let filterWithTo: EventSearchParameters
      let queryWithToTimeAdditions: DocumentClient.QueryInput
      beforeEach(() => {
        filterWithTo = getFilters()
        filterWithTo.to = date.soon().toISOString()

        queryWithToTimeAdditions = getQuery()
        queryWithToTimeAdditions.KeyConditionExpression += ` AND ${eventsStoreAttributes.sortKey} <= :toTime`
        queryWithToTimeAdditions.ExpressionAttributeValues![':toTime'] = filterWithTo.to
      })

      it('does the query with "to" time filters', async () => {
        await searchEvents(db, config, filterWithTo, getPaginated())
        expect(db.query).to.have.been.calledWithExactly(queryWithToTimeAdditions)
      })
    })

    context('with both time filters', () => {
      let fullFilter: EventSearchParameters
      let fullQuery: DocumentClient.QueryInput
      beforeEach(() => {
        fullFilter = getFilters()
        fullFilter.from = date.recent().toISOString()
        fullFilter.to = date.soon().toISOString()

        fullQuery = getQuery()
        fullQuery.KeyConditionExpression += ` AND ${eventsStoreAttributes.sortKey} BETWEEN :fromTime AND :toTime`
        fullQuery.ExpressionAttributeValues![':fromTime'] = fullFilter.from
        fullQuery.ExpressionAttributeValues![':toTime'] = fullFilter.to
      })

      it('does the query with both time filters', async () => {
        await searchEvents(db, config, fullFilter, getPaginated())
        expect(db.query).to.have.been.calledWithExactly(fullQuery)
      })
    })

    if (requiresExtraQueryToMainTable) {
      it('does an extra query to the main table with the corresponding keys', async () => {
        const firstQueryResponse: Array<Record<string, string>> = [
          {
            [eventsStoreAttributes.partitionKey]: random.alpha(),
            [eventsStoreAttributes.sortKey]: random.alphaNumeric(),
          },
          {
            [eventsStoreAttributes.partitionKey]: random.alpha(),
            [eventsStoreAttributes.sortKey]: random.alphaNumeric(),
          },
        ]

        replace(
          db,
          'query',
          fake.returns({
            promise: fake.returns({
              Items: firstQueryResponse,
            }),
          }) as any
        )

        await searchEvents(db, config, getFilters(), getPaginated())
        expect(db.batchGet).to.have.been.calledWithExactly({
          RequestItems: {
            [config.resourceNames.eventsStore]: {
              ConsistentRead: true,
              Keys: firstQueryResponse.map((record) => {
                return {
                  [eventsStoreAttributes.partitionKey]: record[eventsStoreAttributes.partitionKey],
                  [eventsStoreAttributes.sortKey]: record[eventsStoreAttributes.sortKey],
                }
              }),
            },
          },
        })
      })
    }

    context('with an unsorted page of result items', () => {
      const rewiredModule = rewire('../../src/library/events-searcher-adapter')

      const occurredThirdID = random.uuid(),
        occurredSecondID = random.uuid(),
        occurredFirstID = random.uuid()
      const occurredThirdDate = date.recent(),
        occurredSecondDate = date.recent(10, occurredThirdDate),
        occurredFirstDate = date.recent(10, occurredSecondDate)
      const unsortedResult: Array<EventEnvelope> = [
        buildEventEnvelope(occurredThirdID, occurredThirdDate.toISOString()),
        buildEventEnvelope(occurredFirstID, occurredFirstDate.toISOString()),
        buildEventEnvelope(occurredSecondID, occurredSecondDate.toISOString()),
      ]

      it('the result is converted and sorted in descendant order', async () => {
        let fakeExecuteSearch: SinonSpy
        if (getFilters() && getPaginated()) {
          const unsortedPaginatedResult: PaginatedResult = {
            items: unsortedResult,
            count: unsortedResult.length,
            cursor: { id: '1' },
          }
          fakeExecuteSearch = fake.returns(Promise.resolve(unsortedPaginatedResult))
        } else {
          fakeExecuteSearch = fake.returns(Promise.resolve(unsortedResult))
        }

        const revert = rewiredModule.__set__('executeSearch', fakeExecuteSearch)
        // For extra care, first assert that the result page is truly unordered
        expect(unsortedResult.map((item) => item.entityID)).not.to.be.deep.equal([
          occurredThirdID,
          occurredSecondID,
          occurredFirstID,
        ])

        const res: Array<EventSearchResponse> | PaginatedEventSearchResponse = await rewiredModule.searchEvents(
          db,
          config,
          getFilters(),
          getPaginated()
        )
        console.log(res)

        if (getFilters() && getPaginated()) {
          const result = res as PaginatedEventSearchResponse
          // Check they are sorted
          expect(result.items.map((item) => item.entityID)).to.be.deep.equal([
            occurredThirdID,
            occurredSecondID,
            occurredFirstID,
          ])
          // Check they have the right structure
          for (const item of result.items) {
            expect(item).to.have.keys(['type', 'entity', 'entityID', 'requestID', 'user', 'createdAt', 'value'])
          }
          expect(result.count).to.be.eq(result.items.length)
          expect(result.cursor).to.be.deep.equal({ id: '1' })
        } else {
          const result = res as Array<EventSearchResponse>
          // Check they are sorted
          expect(result.map((item) => item.entityID)).to.be.deep.equal([
            occurredThirdID,
            occurredSecondID,
            occurredFirstID,
          ])
          // Check they have the right structure
          for (const item of result) {
            expect(item).to.have.keys(['type', 'entity', 'entityID', 'requestID', 'user', 'createdAt', 'value'])
          }
        }
        revert()
      })
    })
  }
})

function buildEventEnvelope(id: string, createdAt: string): EventEnvelope {
  return {
    entityID: id,
    createdAt,
    requestID: random.uuid(),
    value: { id: random.uuid() },
    entityTypeName: random.alpha(),
    typeName: random.alpha(),
    kind: 'event',
    superKind: 'domain',
    version: random.number(),
  }
}
