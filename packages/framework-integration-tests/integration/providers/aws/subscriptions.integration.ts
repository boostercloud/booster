import { countSubscriptions, DisconnectableApolloClient, graphQLClientWithSubscriptions, waitForIt } from './utils'
import { random } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { ZenObservable } from 'zen-observable-ts/lib/types'

chai.use(require('chai-as-promised'))

describe('subscriptions', () => {
  describe('the unsubscribe operation', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      client = await graphQLClientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('should delete a subscription when the client calls "unsubscribe"', async () => {
      const mockCartId = random.uuid()
      const mockProductId = random.uuid()
      const originalSubscriptionsCount = await countSubscriptions()
      let subscriptionObservable: ZenObservable.Subscription | undefined

      // First create a subscription
      const subscriptionPromise = new Promise((resolve, reject) => {
        subscriptionObservable = client
          .subscribe({
            variables: {
              cartId: mockCartId,
            },
            query: gql`
              subscription CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                  cartItems
                }
              }
            `,
          })
          .subscribe(resolve)
      })

      // Send a mutation that will send data to the previous subscription
      await client.mutate({
        variables: {
          cartId: mockCartId,
          productId: mockProductId,
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 2 })
          }
        `,
      })

      // Wait for it
      await expect(subscriptionPromise).to.eventually.be.fulfilled

      // Now, ensure that we have one more subscription in the table
      expect(await countSubscriptions()).to.be.equal(originalSubscriptionsCount + 1)

      // Stop the subscription
      subscriptionObservable!.unsubscribe()

      // And not check that the subscriptions count is back to the original value
      await waitForIt(
        () => countSubscriptions(),
        (newCount) => newCount == originalSubscriptionsCount
      )
    })

    it('should delete al subscription when socket is disconnected', async () => {})
  })
})
