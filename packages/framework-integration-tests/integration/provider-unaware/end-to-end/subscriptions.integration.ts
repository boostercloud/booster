import { internet, random } from 'faker'
import { expect } from '../../helper/expect'
import { waitForIt } from '../../helper/sleep'
import { FilterFor } from '@boostercloud/framework-types'
import { DisconnectableApolloClient } from '@boostercloud/application-tester'
import { applicationUnderTest } from './setup'
import { beforeHookProductId } from '../../../src/constants'
import { Observable, gql } from '@apollo/client'

describe('subscriptions', () => {
  let countSubscriptions: () => Promise<number>
  let countConnections: () => Promise<number>
  before(async () => {
    countSubscriptions = applicationUnderTest.count.subscriptions.bind(applicationUnderTest.count)
    countConnections = applicationUnderTest.count.connections.bind(applicationUnderTest.count)
  })

  describe('the "unsubscribe" operation', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      client = await applicationUnderTest.graphql.clientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('should delete a subscription when the client calls "unsubscribe"', async () => {
      const originalSubscriptionsCount = await countSubscriptions()

      // Let's create two subscriptions to the same read model
      cartSubscription(client, random.uuid()).subscribe(() => {})
      const subscriptionObservable = cartSubscription(client, random.uuid()).subscribe(() => {})

      // Wait for the subscriptions to arrive
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 2)

      // Stop one of the subscription
      subscriptionObservable.unsubscribe()

      // And now check that the new subscriptions count down by one
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
    })
  })

  describe('the "terminate" operation', () => {
    it('should delete all subscription of the connectionID when socket is disconnected', async () => {
      const clientA = await applicationUnderTest.graphql.clientWithSubscriptions()
      const clientB = await applicationUnderTest.graphql.clientWithSubscriptions()
      try {
        const originalSubscriptionsCount = await countSubscriptions()

        // Let's create one subscription for one client
        cartSubscription(clientA, random.uuid()).subscribe(() => {})

        // Let's create two subscriptions for another client
        cartSubscription(clientB, random.uuid()).subscribe(() => {})
        cartSubscription(clientB, random.uuid()).subscribe(() => {})

        // Wait for the subscriptions to arrive
        await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 3)

        // Now we close the socket of client B and check its 2 subscriptions were deleted
        clientB.disconnect()
        await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)

        // Finally, close the socket of client A and check that we are back to the original count of subscriptions
        clientA.disconnect()
        await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount)
      } catch (e) {
        clientA.disconnect()
        clientB.disconnect()
      }
    })

    it('should delete connection data when socket is disconnected', async () => {
      const connectionsCount = await countConnections()
      const client = await applicationUnderTest.graphql.clientWithSubscriptions()
      try {
        await waitForIt(countConnections, (newCount) => newCount == connectionsCount + 1)
        client.disconnect()
        await waitForIt(countConnections, (newCount) => newCount == connectionsCount)
      } catch {
        client.disconnect()
      }
    })
  })

  describe('when socket reconnects ', () => {
    let clients: DisconnectableApolloClient[]
    const clientCount = 2
    before(async () => {
      clients = []
      for (let i = 0; i < clientCount; i++)
        clients.push(await applicationUnderTest.graphql.clientWithSubscriptions())
    })
    after(() => {
      clients.forEach(c => c.disconnect())
    })

    it('keeps the same subscriptions', async () => {
      const cartID = random.uuid()
      const originalSubscriptionsCount = await countSubscriptions()
      const observables = clients.map(c => cartSubscription(c, cartID))
      observables.forEach(o => o.subscribe(() => {}));
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + clientCount)
      await verifySubscriptionsActive()
      await Promise.all(clients.map(c => c.reconnect()))
      await verifySubscriptionsActive()

      async function verifySubscriptionsActive() {
        await cartMutation(clients[0], cartID)
        await expect(Promise.all(observables.map(promisifyNextSubscriptionResult))).to.eventually.be.fulfilled
      }
    })
  })

  describe('with filters', () => {
    let client: DisconnectableApolloClient
    before(async () => {
      client = await applicationUnderTest.graphql.clientWithSubscriptions()
    })
    after(() => {
      client.disconnect()
    })

    it('get a carts with a specific ID', async () => {
      const cartID = random.uuid()

      const originalSubscriptionsCount = await countSubscriptions()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, { id: { eq: cartID } })
      // Call the subscribe function to send the subscription to server
      observable.subscribe(() => {})
      // Wait for the subscriptions to arrive
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID)
      const result = await promisifyNextSubscriptionResult(observable)
      const cart = result.data.CartReadModels
      expect(cart.id).to.equal(cartID)
    })

    it('filters based on before hooks when user sends filters in the subscription query', async () => {
      const cartID = 'before-fn-test'

      const originalSubscriptionsCount = await countSubscriptions()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, { id: { eq: cartID } })
      // Call the subscribe function to send the subscription to server
      const subscriptionPromise = promisifyNextSubscriptionResult(observable)
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, 'before-fn-test-modified', beforeHookProductId)

      const result = await subscriptionPromise
      const cart = result.data.CartReadModels

      expect(cart.id).to.equal('before-fn-test-modified')
    })

    it('filters based on single before hooks when user don not send filters but a specific id', async () => {
      const cartID = 'before-fn-test'

      const originalSubscriptionsCount = await countSubscriptions()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSingleIDSubscription(client, cartID)
      // Call the subscribe function to send the subscription to server
      const subscriptionPromise = promisifyNextSubscriptionResult(observable)
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, 'before-fn-test-modified', beforeHookProductId)

      const result = await subscriptionPromise
      const cart = result.data.CartReadModel

      expect(cart.id).to.equal('before-fn-test-modified')
    })

    it('get the carts with an specific product id', async () => {
      const cartID = random.uuid()
      const productId = random.uuid()

      const originalSubscriptionsCount = await countSubscriptions()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, {
        cartItems: { includes: { productId: productId, quantity: 2 } },
      })
      // Call the subscribe function to send the subscription to server
      observable.subscribe(() => {})
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID, productId)
      const result = await promisifyNextSubscriptionResult(observable)
      const cart = result.data.CartReadModels
      expect(cart.id).to.equal(cartID)

      const isWellFiltered = cart.cartItems.some((cartItem: { productId: string }) => cartItem.productId === productId)
      expect(isWellFiltered).to.be.true
    })

    it('get the cart filtering by an array of strings', async () => {
      const cartID = random.uuid()
      const productId = random.uuid()

      const originalSubscriptionsCount = await countSubscriptions()
      // Let's create two subscriptions to the same read model
      const observable = cartFilteredSubscription(client, {
        cartItemsIds: { includes: productId },
      })
      // Call the subscribe function to send the subscription to server
      observable.subscribe(() => {})
      // Wait for for the subscriptions to arrive
      await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
      // Check we receive data when the read model is modified
      await cartMutation(client, cartID, productId)
      const result = await promisifyNextSubscriptionResult(observable)
      const cart = result.data.CartReadModels

      expect(cart.id).to.equal(cartID)

      const isWellFiltered = cart.cartItemsIds.some((cartItemId: string) => cartItemId === productId)
      expect(isWellFiltered).to.be.true
    })
  })

  describe('readmodel authorization', () => {
    context('with an anonymous user', () => {
      let client: DisconnectableApolloClient

      beforeEach(async () => {
        client = await applicationUnderTest.graphql.clientWithSubscriptions()
      })

      afterEach(() => {
        client.disconnect()
      })

      context('with a read model authorized for matching roles', () => {
        it('should not be accessible', async () => {
          const productId = random.uuid()

          const observable = productSubscription(client, productId)
          let error: undefined | { message: string }
          observable.subscribe(
            () => {},
            (err: any) => {
              error = err
            }
          )
          await waitForIt(
            () => Promise.resolve(error),
            (error) => error !== undefined && error.message === 'Access denied for this resource'
          )
        })
      })
    })
    
    context('with a user without the required role', () => {
      let loggedClient: DisconnectableApolloClient

      beforeEach(async () => {
        const userToken = applicationUnderTest.token.forUser(internet.email(), 'UserThatHasNoBusinesWithProducts')
        loggedClient = await applicationUnderTest.graphql.clientWithSubscriptions(userToken)
      })

      afterEach(() => {
        loggedClient.disconnect()
      })

      context('with a read model authorized for matching roles', () => {
        it('should not be accessible', async () => {
          const productId = random.uuid()

          const observable = productSubscription(loggedClient, productId)
          let error: undefined | { message: string }
          observable.subscribe(
            () => {},
            (err: any) => {
              error = err
            }
          )
          await waitForIt(
            () => Promise.resolve(error),
            (error) => error !== undefined && error.message === 'Access denied for this resource'
          )
        })
      })
    })

    context('with a user with the required role', () => {
      let loggedClient: DisconnectableApolloClient

      beforeEach(async () => {
        const userToken = applicationUnderTest.token.forUser(internet.email(), 'UserWithEmail')
        loggedClient = await applicationUnderTest.graphql.clientWithSubscriptions(userToken)
      })

      afterEach(() => {
        loggedClient.disconnect()
      })

      context('with a read model authorized for matching roles', () => {
        it('should be accessible', async () => {
          const productId = random.uuid()

          const originalSubscriptionsCount = await countSubscriptions()
          const observable = productSubscription(loggedClient, productId)
          observable.subscribe(() => {})
          await waitForIt(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1)
          await productMutation(loggedClient, productId)
          const result = await promisifyNextSubscriptionResult(observable)
          const productReadModel = result.data.ProductReadModel
          expect(productReadModel.id).to.equal(productId)
        })
      })
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
          cartItems {
            productId
            quantity
          }
        }
      }
    `,
  })
}

function productSubscription(client: DisconnectableApolloClient, productId: string): Observable<any> {
  return client.subscribe({
    variables: { productId: productId },
    query: gql`
      subscription ProductReadModel($productId: ID!) {
        ProductReadModel(id: $productId) {
          id
          price {
            cents
            currency
          }
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
          cartItems {
            productId
            quantity
          }
          cartItemsIds
        }
      }
    `,
  })
}

function cartFilteredSingleIDSubscription(client: DisconnectableApolloClient, cartId: string): Observable<any> {
  return client.subscribe({
    variables: { cartId, random: 'variable' },
    query: gql`
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
          cartItemsIds
        }
      }
    `,
  })
}

function promisifyNextSubscriptionResult(observable: Observable<any>): Promise<any> {
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

async function productMutation(client: DisconnectableApolloClient, productId: string): Promise<void> {
  const sku = random.uuid()
  await client.mutate({
    variables: {
      sku: sku,
      productID: productId,
    },
    mutation: gql`
      mutation CreateProduct($sku: String!, $productID: ID) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            priceInCents: 1.0
            displayName: "product"
            description: "product"
            currency: "ANY"
          }
        )
      }
    `,
  })
}
