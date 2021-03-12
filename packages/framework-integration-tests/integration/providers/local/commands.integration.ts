import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { changeCartItem, graphQLClient } from './utils'
import { random } from 'faker'
import { expect } from 'chai'
import * as DataStore from 'nedb'
import { sandboxPath } from './constants'
import util = require('util')
import * as path from 'path'
import { waitForIt } from '../../helper/sleep'

describe('commands', () => {
  let events: DataStore<unknown>

  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    events = new DataStore(path.join(sandboxPath, '.booster', 'events.json'))
    client = await graphQLClient()
  })

  context('valid command', () => {
    let mockCartId: string
    let mockProductId: string
    let mockQuantity: number

    beforeEach(() => {
      mockCartId = random.uuid()
      mockProductId = random.uuid()
      mockQuantity = random.number({ min: 1 })
    })

    xit('should successfully process a command', async () => {
      const mutationResult = await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      expect(mutationResult).not.to.be.null
      expect(mutationResult.data.ChangeCartItem).to.be.true
    })

    xit('should store event in the database', async () => {
      await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      // Wait until event is stored in database
      await waitForIt(
        async () => events.loadDatabase(),
        () => events.getAllData().some((value) => value.entityID === mockCartId)
      )

      // Verify the event content
      const result = await new Promise((resolve, reject) =>
        events.findOne({ entityID: mockCartId }, (err, docs) => {
          err ? reject(err) : resolve(docs)
        })
      )

      const expectedResult = {
        version: 1,
        kind: 'event',
        entityID: mockCartId,
        currentUser: {
          username: 'test@test.com',
          role: '',
        },
        entityTypeName: 'Cart',
        typeName: 'CartItemChanged',
        value: {
          cartId: mockCartId,
          productId: mockProductId,
          quantity: mockQuantity,
        },
      }

      expect(result).to.deep.include(expectedResult)
    })

    xit('should create a snapshot after 5 events', async () => {
      let mockQuantity: number
      let expectedSnapshotQuantity = 0

      for (let i = 0; i < 5; i++) {
        mockQuantity = random.number()
        expectedSnapshotQuantity += mockQuantity
        await changeCartItem(client, mockCartId, mockProductId, mockQuantity)
      }

      // Sixth event - Quantity shouldn't be added to snapshot
      mockQuantity = random.number()
      await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      await waitForIt(
        async () => events.loadDatabase(),
        () =>
          events
            .getAllData()
            .some(
              (record) =>
                record.entityID === mockCartId &&
                record.kind === 'snapshot' &&
                record.value?.cartItems[0]?.productId === mockProductId &&
                record.value?.cartItems[0]?.quantity === expectedSnapshotQuantity
            )
      )

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const countPromise = util.promisify((query: any, callback: any) => events.count(query, callback))
      expect(await countPromise({ kind: 'snapshot', entityID: mockCartId, entityTypeName: 'Cart' })).to.be.gte(1)
    })
  })
})
