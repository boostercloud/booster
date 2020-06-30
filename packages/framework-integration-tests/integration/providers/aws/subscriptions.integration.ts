import { countSubscriptions, DisconnectableApolloClient, graphQLClientWithSubscriptions, waitForIt } from './utils'
import { random } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { ZenObservable } from 'zen-observable-ts/lib/types'
import { Observable } from 'apollo-client/util/Observable'

chai.use(require('chai-as-promised'))

describe('subscriptions', () => {
  describe('the "unsubscribe" operation', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      client = await graphQLClientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('should delete a subscription when the client calls "unsubscribe"', async () => {
      const mockCartId = random.uuid()
      const originalSubscriptionsCount = await countSubscriptions()
      let subscriptionObservable: ZenObservable.Subscription | undefined

      // Let's create two subscriptions to the same read model
      const nonInterestingSubscriptionPromise = new Promise((resolve, reject) => {
        cartSubscription(client, mockCartId).subscribe(resolve)
      })

      const subscriptionPromise = new Promise((resolve, reject) => {
        subscriptionObservable = cartSubscription(client, mockCartId).subscribe(resolve)
      })

      // Send a mutation that will send data to both subscriptions
      await addACartItem(client, mockCartId)
      // Wait for for the subscriptions to receive the data
      await expect(subscriptionPromise).to.eventually.be.fulfilled
      await expect(nonInterestingSubscriptionPromise).to.eventually.be.fulfilled

      // Now, ensure that we have 2 more subscription in the table
      expect(await countSubscriptions()).to.be.equal(originalSubscriptionsCount + 2)

      // Stop one of the subscription
      subscriptionObservable!.unsubscribe()

      // And now check that the new subscriptions count down by one
      await waitForIt(
        () => countSubscriptions(),
        (newCount) => newCount == originalSubscriptionsCount + 1
      )
    })
  })

  describe('the "terminate" operation', () => {
    it('should delete al subscription of the connectionID when socket is disconnected', async () => {
      const originalSubscriptionsCount = await countSubscriptions()

      // Let's create one subscription for one client
      const clientA = await graphQLClientWithSubscriptions()
      const mockCartId = random.uuid()
      const clientASubscriptionPromise = new Promise((resolve, reject) => {
        cartSubscription(clientA, mockCartId).subscribe(resolve)
      })

      // Let's create two subscriptions for another client
      const clientB = await graphQLClientWithSubscriptions()
      const clientBSubscriptionPromiseOne = new Promise((resolve, reject) => {
        cartSubscription(clientB, mockCartId).subscribe(resolve)
      })
      const clientBSubscriptionPromiseTwo = new Promise((resolve, reject) => {
        cartSubscription(clientB, mockCartId).subscribe(resolve)
      })

      // Send a mutation that will send data to all three subscriptions
      await addACartItem(clientA, mockCartId)
      // Wait for for the subscriptions to receive the data
      await expect(clientASubscriptionPromise).to.eventually.be.fulfilled
      await expect(clientBSubscriptionPromiseOne).to.eventually.be.fulfilled
      await expect(clientBSubscriptionPromiseTwo).to.eventually.be.fulfilled

      // Ensure that we have 3 more subscription in the table
      expect(await countSubscriptions()).to.be.equal(originalSubscriptionsCount + 3)

      // Now we close the socket of client B and check its 2 subscriptions were deleted
      clientB.disconnect()
      await waitForIt(
        () => countSubscriptions(),
        (newCount) => newCount == originalSubscriptionsCount + 1
      )

      // Finally, close the socket of client A and check that we are back to the original count of subscriptions
      clientA.disconnect()
      await waitForIt(
        () => countSubscriptions(),
        (newCount) => newCount == originalSubscriptionsCount
      )
    })
  })
})

function cartSubscription(client: DisconnectableApolloClient, cartID: string): Observable<any> {
  return client.subscribe({
    variables: { cartId: cartID },
    query: gql`
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems
        }
      }
    `,
  })
}

function addACartItem(client: DisconnectableApolloClient, cartID: string): Promise<any> {
  return client.mutate({
    variables: {
      cartId: cartID,
      productId: random.uuid(),
    },
    mutation: gql`
      mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 2 })
      }
    `,
  })
}
