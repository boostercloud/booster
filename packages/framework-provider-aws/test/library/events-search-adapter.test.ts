/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, restore, SinonStubbedInstance, fake, replace } from 'sinon'
import {
  BoosterConfig,
  EventEnvelope,
  EventFilter,
  EventFilterByEntity,
  EventFilterByType,
  EventSearchResponse,
  Logger,
} from '@boostercloud/framework-types'
import { random, date } from 'faker'
import { DynamoDB } from 'aws-sdk'
import { searchEvents } from '../../src/library/events-searcher-adapter'
import { eventsStoreAttributes } from '../../src'
import { partitionKeyForEvent, partitionKeyForIndexByEntity } from '../../src/library/keys-helper'
import { DocumentClient } from 'aws-sdk/clients/dynamodb'
import rewire = require('rewire')

describe('Events searcher adapter', () => {
  const config: BoosterConfig = new BoosterConfig('test')
  const logger: Logger = console
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
      await expect(searchEvents(db, config, logger, {} as never)).to.be.rejectedWith(/Invalid search event query/)
    })

    describe('for a search by entity with ID', () => {
      let filter: EventFilterByEntity
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
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            ConsistentRead: true,
            ScanIndexForward: false,
            KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: { ':partitionKey': partitionKeyForEvent(filter.entity, entityID) },
          }
        }
      )
    })

    describe('for a search by entity and no ID', () => {
      let filter: EventFilterByEntity
      beforeEach(() => {
        filter = {
          entity: random.alpha(),
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            IndexName: eventsStoreAttributes.indexByEntity.name(config),
            ScanIndexForward: false,
            KeyConditionExpression: `${eventsStoreAttributes.indexByEntity.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: {
              ':partitionKey': partitionKeyForIndexByEntity(filter.entity, 'event'),
            },
          }
        },
        true
      )
    })

    describe('for a search by type', () => {
      let filter: EventFilterByType
      beforeEach(() => {
        filter = {
          type: random.alpha(),
        }
      })

      runQueryTestsWithTimeFiltersVariants(
        () => filter,
        () => {
          return {
            TableName: config.resourceNames.eventsStore,
            IndexName: eventsStoreAttributes.indexByType.name(config),
            ScanIndexForward: false,
            KeyConditionExpression: `${eventsStoreAttributes.indexByType.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: {
              ':partitionKey': filter.type,
            },
          }
        },
        true
      )
    })
  })

  function runQueryTestsWithTimeFiltersVariants(
    getFilters: () => EventFilter,
    getQuery: () => DocumentClient.QueryInput,
    requiresExtraQueryToMainTable = false
  ): void {
    context('with no time filters', () => {
      it('does the right query', async () => {
        await searchEvents(db, config, logger, getFilters())
        expect(db.query).to.have.been.calledWithExactly(getQuery())
      })
    })

    context('with "from" time filter', () => {
      let filterWithFrom: EventFilter
      let queryWithFromTimeAdditions: DocumentClient.QueryInput
      beforeEach(() => {
        filterWithFrom = getFilters()
        filterWithFrom.from = date.recent().toISOString()

        queryWithFromTimeAdditions = getQuery()
        queryWithFromTimeAdditions.KeyConditionExpression += ` AND ${eventsStoreAttributes.sortKey} >= :fromTime`
        queryWithFromTimeAdditions.ExpressionAttributeValues![':fromTime'] = filterWithFrom.from
      })

      it('does the right query', async () => {
        await searchEvents(db, config, logger, filterWithFrom)
        expect(db.query).to.have.been.calledWithExactly(queryWithFromTimeAdditions)
      })
    })

    context('with "to" time filters', () => {
      let filterWithTo: EventFilter
      let queryWithToTimeAdditions: DocumentClient.QueryInput
      beforeEach(() => {
        filterWithTo = getFilters()
        filterWithTo.to = date.soon().toISOString()

        queryWithToTimeAdditions = getQuery()
        queryWithToTimeAdditions.KeyConditionExpression += ` AND ${eventsStoreAttributes.sortKey} <= :toTime`
        queryWithToTimeAdditions.ExpressionAttributeValues![':toTime'] = filterWithTo.to
      })

      it('does the right query', async () => {
        await searchEvents(db, config, logger, filterWithTo)
        expect(db.query).to.have.been.calledWithExactly(queryWithToTimeAdditions)
      })
    })

    context('with both time filters', () => {
      let fullFilter: EventFilter
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

      it('does the right query', async () => {
        await searchEvents(db, config, logger, fullFilter)
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

        await searchEvents(db, config, logger, getFilters())
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
      const fakeExecuteSearch = fake.returns(Promise.resolve(unsortedResult))
      let revert: () => void

      beforeEach(() => {
        revert = rewiredModule.__set__('executeSearch', fakeExecuteSearch)
      })
      afterEach(() => {
        revert()
      })

      it('the result is converted and sorted in descendant order', async () => {
        // For extra care, first assert that the result page is truly unordered
        expect(unsortedResult.map((item) => item.entityID)).not.to.be.deep.equal([
          occurredThirdID,
          occurredSecondID,
          occurredFirstID,
        ])

        const res: Array<EventSearchResponse> = await rewiredModule.searchEvents(db, config, logger, getFilters())
        console.log(res)

        // Check they are sorted
        expect(res.map((item) => item.entityID)).to.be.deep.equal([occurredThirdID, occurredSecondID, occurredFirstID])
        // Check they have the right structure
        for (const item of res) {
          expect(item).to.have.keys(['type', 'entity', 'entityID', 'requestID', 'user', 'createdAt', 'value'])
        }
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
    version: random.number(),
  }
}
