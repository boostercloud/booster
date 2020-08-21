import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient } from './utils'
import gql from 'graphql-tag'
import { random } from 'faker'
import { expect } from 'chai'
import * as DataStore from 'nedb'
import { eventsDatabase } from '@boostercloud/framework-provider-local'
import { EventEnvelope } from '@boostercloud/framework-types'
import { waitForIt } from '../aws/utils'
import util = require('util')

describe('commands', () => {
  const events: DataStore<EventEnvelope> = new DataStore(eventsDatabase)

  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
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

    it('should successfully process a command', async () => {
      const mutationResult = await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      expect(mutationResult).not.to.be.null
      expect(mutationResult.data.ChangeCartItem).to.be.true
    })

    it('should store event in the database', async () => {
      await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      // Wait until event is stored in database
      await waitForIt(
        async () => events.loadDatabase(),
        (_) => events.getAllData().some((value) => value.entityID === mockCartId)
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

    it('should create a snapshot after 5 events', async () => {
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
        (_) =>
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

      const countPromise = util.promisify((query: any, callback: any) => events.count(query, callback))
      expect(await countPromise({ kind: 'snapshot', entityID: mockCartId })).to.be.equal(1)
    })
  })
})

async function changeCartItem(
  client: ApolloClient<NormalizedCacheObject>,
  cartId: string,
  productId: string,
  quantity: number
): Promise<any> {
  return client.mutate({
    variables: {
      cartId: cartId,
      productId: productId,
      quantity: quantity,
    },
    mutation: gql`
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
    `,
  })
}
