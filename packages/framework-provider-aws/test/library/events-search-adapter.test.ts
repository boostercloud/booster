/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { createStubInstance, restore, stub, SinonStubbedInstance } from 'sinon'
import { BoosterConfig, EventFilterByEntity, Logger } from '@boostercloud/framework-types'
import { random, date } from 'faker'
import { DynamoDB } from 'aws-sdk'
import { searchEvents } from '../../src/library/events-searcher-adapter'
import { eventsStoreAttributes } from '../../src'
import { partitionKeyForEvent } from '../../src/library/partition-keys'

describe('Events searcher adapter', () => {
  describe('The "searchEvents" method', () => {
    const config: BoosterConfig = new BoosterConfig('test')
    const logger: Logger = console

    let db: SinonStubbedInstance<DynamoDB.DocumentClient>
    beforeEach(() => {
      db = createStubInstance(DynamoDB.DocumentClient, {
        query: {
          promise: stub().returns({
            result: stub().returns({}),
          }),
        } as any,
      })
    })
    after(() => {
      restore()
    })

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

      context('with no time filters', () => {
        it('does the right query', async () => {
          await searchEvents(db, config, logger, filter)
          expect(db.query).to.have.been.calledWithExactly({
            TableName: config.resourceNames.eventsStore,
            ConsistentRead: true,
            ScanIndexForward: false,
            KeyConditionExpression: `${eventsStoreAttributes.partitionKey} = :partitionKey`,
            ExpressionAttributeValues: { ':partitionKey': partitionKeyForEvent(filter.entity, entityID) },
          })
        })
      })

      context('with time filters', () => {
        beforeEach(() => {
          filter.from = date.recent().toISOString()
          filter.to = date.soon().toISOString()
        })
        it('does the right query', async () => {
          await searchEvents(db, config, logger, filter)
          expect(db.query).to.have.been.calledWithExactly({
            TableName: config.resourceNames.eventsStore,
            ConsistentRead: true,
            ScanIndexForward: false,
            KeyConditionExpression:
              `${eventsStoreAttributes.partitionKey} = :partitionKey` +
              ` AND ${eventsStoreAttributes.sortKey} BETWEEN :fromTime AND :toTime`,
            ExpressionAttributeValues: {
              ':fromTime': filter.from,
              ':toTime': filter.to,
              ':partitionKey': partitionKeyForEvent(filter.entity, entityID),
            },
          })
        })
      })
    })
  })
})
