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
      const mutationResult = await client.mutate({
        variables: {
          cartId: mockCartId,
          productId: mockProductId,
          quantity: mockQuantity,
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
      })

      expect(mutationResult).not.to.be.null
      expect(mutationResult.data.ChangeCartItem).to.be.true
    })

    it('should store event in the database', async () => {
      await client.mutate({
        variables: {
          cartId: mockCartId,
          productId: mockProductId,
          quantity: mockQuantity,
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
      })

      // Wait until event is stored in database
      await waitForIt(
        () =>
          new Promise((resolve) => {
            events.loadDatabase()
            resolve()
          }),
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
          email: 'test@test.com',
          roles: [],
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
  })
})
