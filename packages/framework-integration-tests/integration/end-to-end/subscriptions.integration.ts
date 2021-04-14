import {
  countConnectionsItems,
  countSubscriptionsItems,
  DisconnectableApolloClient,
  graphQLClientWithSubscriptions,
} from '../providers/aws/utils'
import { random } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { Observable } from 'apollo-client/util/Observable'
import { waitForIt } from '../helper/sleep'
import { FilterFor } from '@boostercloud/framework-types'

chai.use(require('chai-as-promised'))

describe('subscriptions', () => {
  describe('the "unsubscribe" operation', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      // TODO: make tests cloud agnostic
      client = await graphQLClientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('should delete a subscription when the client calls "unsubscribe"', async () => {
      const originalSubscriptionsCount = await countSubscriptionsItems()

      // Let's create two subscriptions to the same read model
      cartSubscription(client, random.uuid()).subscribe(() => {})
      const subscriptionObservable = cartSubscription(client, random.uuid()).subscribe(() => {})

      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 2)

      // Stop one of the subscription
      subscriptionObservable.unsubscribe()

      // And now check that the new subscriptions count down by one
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 1)
    })
  })

  describe('the "terminate" operation', () => {
    it('should delete all subscription of the connectionID when socket is disconnected', async () => {
      const clientA = await graphQLClientWithSubscriptions()
      const clientB = await graphQLClientWithSubscriptions()
      try {
        const originalSubscriptionsCount = await countSubscriptionsItems()

        // Let's create one subscription for one client
        cartSubscription(clientA, random.uuid()).subscribe(() => {})

        // Let's create two subscriptions for another client
        cartSubscription(clientB, random.uuid()).subscribe(() => {})
        cartSubscription(clientB, random.uuid()).subscribe(() => {})

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

    it('should delete connection data when socket is disconnected', async () => {
      const connectionsCount = await countConnectionsItems()
      const client = await graphQLClientWithSubscriptions()
      try {
        await waitForIt(countConnectionsItems, (newCount) => newCount == connectionsCount + 1)
        client.disconnect()
        await waitForIt(countConnectionsItems, (newCount) => newCount == connectionsCount)
      } catch {
        client.disconnect()
      }
    })
  })

  describe('when socket reconnects ', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      client = await graphQLClientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('keeps the same subscriptions', async () => {
      const cartID = random.uuid()
      const originalSubscriptionsCount = await countSubscriptionsItems()
      // Let's create two subscriptions to the same read model
      const observableOne = cartSubscription(client, cartID)
      const observableTwo = cartSubscription(client, cartID)
      // Call the subscribe function to send the subscription to server
      observableOne.subscribe(() => {})
      observableTwo.subscribe(() => {})
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 2)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID)
      await expect(Promise.all([promisify(observableOne), promisify(observableTwo)])).to.eventually.be.fulfilled

      // Now reconnect and see if we keep the having the same subscription and receive data
      await client.reconnect()
      await cartMutation(client, cartID)
      await expect(Promise.all([promisify(observableOne), promisify(observableTwo)])).to.eventually.be.fulfilled
    })
  })

  describe('with filters', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      client = await graphQLClientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('get a carts with a specific ID', async () => {
      const cartID = random.uuid()

      const originalSubscriptionsCount = await countSubscriptionsItems()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, { id: { eq: cartID } })
      // Call the subscribe function to send the subscription to server
      observable.subscribe(() => {})
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID)
      const result = await promisify(observable)
      const cart = result.data.CartReadModels
      expect(cart.id).to.equal(cartID)
    })

    it('get the carts with an specific product id', async () => {
      const cartID = random.uuid()
      const productId = random.uuid()

      const originalSubscriptionsCount = await countSubscriptionsItems()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, {
        cartItems: { includes: { productId: productId, quantity: 2 } },
      })
      // Call the subscribe function to send the subscription to server
      observable.subscribe(() => {})
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID, productId)
      const result = await promisify(observable)
      const cart = result.data.CartReadModels
      expect(cart.id).to.equal(cartID)

      const isWellFiltered = cart.cartItems.some((cartItem: { productId: string }) => cartItem.productId === productId)
      expect(isWellFiltered).to.be.true
    })

    it('get the cart filtering by an array of strings', async () => {
      const cartID = random.uuid()
      const productId = random.uuid()

      const originalSubscriptionsCount = await countSubscriptionsItems()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, {
        cartItemsIds: { includes: productId },
      })
      // Call the subscribe function to send the subscription to server
      observable.subscribe(() => {})
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptionsItems, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID, productId)
      const result = await promisify(observable)
      const cart = result.data.CartReadModels

      expect(cart.id).to.equal(cartID)

      const isWellFiltered = cart.cartItemsIds.some((cartItemId: string) => cartItemId === productId)
      expect(isWellFiltered).to.be.true
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

function cartFilteredSubscription(client: DisconnectableApolloClient, filter: FilterFor<any>): Observable<any> {
  return client.subscribe({
    variables: { filter },
    query: gql`
      subscription CartReadModels($filter: CartReadModelSubscriptionFilter) {
        CartReadModels(filter: $filter) {
          id
          cartItems
          cartItemsIds
        }
      }
    `,
  })
}

function promisify(observable: Observable<any>): Promise<any> {
  return new Promise((resolve, reject) => {
    observable.subscribe({
      next: resolve,
      error: reject,
    })
  })
}

async function cartMutation(
  client: DisconnectableApolloClient,
  cartID: string,
  productId: string = random.uuid()
): Promise<void> {
  await client.mutate({
    variables: {
      cartId: cartID,
      productId: productId,
    },
    mutation: gql`
      mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 2 })
      }
    `,
  })
}
