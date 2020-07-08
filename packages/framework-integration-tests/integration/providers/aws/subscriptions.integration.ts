import { countSubscriptionsItems, DisconnectableApolloClient, graphQLClientWithSubscriptions, waitForIt } from './utils'
import { random } from 'faker'
import gql from 'graphql-tag'
import * as chai from 'chai'
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
      const originalSubscriptionsCount = await countSubscriptionsItems()

      // Let's create two subscriptions to the same read model
      cartSubscription(client, mockCartId).subscribe(() => {})
      const subscriptionObservable = cartSubscription(client, mockCartId).subscribe(() => {})

      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 2)

      // Stop one of the subscription
      subscriptionObservable.unsubscribe()

      // And now check that the new subscriptions count down by one
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 1)
    })
  })

  describe('the "terminate" operation', () => {
    it('should delete al subscription of the connectionID when socket is disconnected', async () => {
      const clientA = await graphQLClientWithSubscriptions()
      const clientB = await graphQLClientWithSubscriptions()
      try {
        const originalSubscriptionsCount = await countSubscriptionsItems()
        const mockCartId = random.uuid()

        // Let's create one subscription for one client
        cartSubscription(clientA, mockCartId).subscribe(() => {})

        // Let's create two subscriptions for another client
        cartSubscription(clientB, mockCartId).subscribe(() => {})
        cartSubscription(clientB, mockCartId).subscribe(() => {})

        // Wait for for the subscriptions to arrive
        await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 3)

        // Now we close the socket of client B and check its 2 subscriptions were deleted
        clientB.disconnect()
        await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 1)

        // Finally, close the socket of client A and check that we are back to the original count of subscriptions
        clientA.disconnect()
        await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount)
      } catch (e) {
        clientA.disconnect()
        clientB.disconnect()
      }
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
