import {
  graphQLClientWithSubscriptions,
  DisconnectableApolloClient,
  countSubscriptionsItems,
  graphQLClient,
  getTokenForUser,
} from '../utils'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { random, internet, finance, lorem } from 'faker'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { waitForIt } from '../../../helper/sleep'
import { createPassword } from '../../../helper/auth-helper'

chai.use(require('chai-as-promised'))

describe('With the auth API', () => {
  let mockProductId: string
  let mockProductSKU: string
  let mockCartId: string

  beforeEach(() => {
    mockProductId = random.uuid()
    mockProductSKU = random.alphaNumeric(6)
    mockCartId = random.uuid()
  })

  context('an internet rando', () => {
    let client: DisconnectableApolloClient

    before(async () => {
      client = await graphQLClientWithSubscriptions()
    })

    after(() => {
      client.disconnect()
    })

    it("can't submit a secured command", async () => {
      const mutationPromise = client.mutate({
        mutation: gql`
          mutation {
            CreateProduct(
              input: {
                sku: "314"
                displayName: "Something fancy"
                description: "It's really fancy"
                priceInCents: 4000
                currency: "EUR"
              }
            )
          }
        `,
      })

      await expect(mutationPromise).to.eventually.be.rejectedWith("Access denied for command 'CreateProduct'")
    })

    it("can't query a secured read model", async () => {
      const queryPromise = client.query({
        variables: {
          productId: mockProductId,
        },
        query: gql`
          query ProductUpdatesReadModel($productId: ID!) {
            ProductUpdatesReadModel(id: $productId) {
              id
              availability
              lastUpdate
              previousUpdate
            }
          }
        `,
      })

      await expect(queryPromise).to.eventually.be.rejectedWith('Access denied for read model ProductUpdatesReadModel')
    })

    it("can't subscribe to a secured read model", async () => {
      const subscription = await client.subscribe({
        variables: {
          productId: mockProductId,
        },
        query: gql`
          subscription ProductUpdatesReadModel($productId: ID!) {
            ProductUpdatesReadModel(id: $productId) {
              id
              availability
              lastUpdate
              previousUpdate
            }
          }
        `,
      })

      const subscriptionPromise = new Promise((_, reject) => {
        subscription.subscribe({
          // This "subscribe" is the one of the Observable returned by Apollo
          error: reject,
        })
      })

      await expect(subscriptionPromise).to.eventually.be.rejectedWith(
        /Access denied for read model ProductUpdatesReadModel/
      )
    })

    it('can submit a public command', async () => {
      const mutationResult = await client.mutate({
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

      expect(mutationResult).not.to.be.null
      expect(mutationResult.data.ChangeCartItem).to.be.true
    })

    it('can query a public read model', async () => {
      mockCartId = random.uuid()
      mockProductId = random.uuid()

      // Provision cart
      const mutationResult = await client.mutate({
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

      expect(mutationResult).not.to.be.null
      expect(mutationResult.data.ChangeCartItem).to.be.true

      // Query cart read model
      const queryResult = await waitForIt(
        () => {
          return client.query({
            variables: {
              cartId: mockCartId,
            },
            query: gql`
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                  cartItems
                }
              }
            `,
          })
        },
        (result) => result?.data?.CartReadModel != null
      )

      // Result should match the cart created in the previous test case
      expect(queryResult.data.CartReadModel.id).to.be.equal(mockCartId)
      expect(queryResult.data.CartReadModel.cartItems[0]).to.deep.equal({
        productId: mockProductId,
        quantity: 2,
      })
    })

    it('can subscribe to a public read model', async () => {
      const currentSubscriptionsCount = await countSubscriptionsItems()

      // We check that we receive data after modifying the read model with a command
      const subscription = client.subscribe({
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

      const subscriptionPromise = new Promise((resolve, reject) => {
        subscription.subscribe({
          // This "subscribe" is the one of the Observable returned by Apollo
          next: resolve,
          error: reject,
        })
      })

      await waitForIt(countSubscriptionsItems, (count) => count > currentSubscriptionsCount)

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

      await expect(subscriptionPromise).to.eventually.be.fulfilled
    })
  })

  context('someone with a user with email account', () => {
    context('with a wrong token', () => {
      context('using a client without involving sockets (no subscriptions)', () => {
        let client: ApolloClient<NormalizedCacheObject>

        before(async () => {
          client = await graphQLClient('ABC')
        })

        it('gets the expected error when submitting a command', async () => {
          const mutationPromise = client.mutate({
            variables: {
              productSKU: random.word(),
            },
            mutation: gql`
              mutation CreateProduct($productSKU: String) {
                CreateProduct(input: { sku: $productSKU })
              }
            `,
          })

          await expect(mutationPromise).to.eventually.be.rejectedWith(/jwt malformed/)
        })

        it('gets the expected error when querying a read model', async () => {
          const queryPromise = client.query({
            variables: {
              productId: mockProductId,
            },
            query: gql`
              query ProductUpdatesReadModel($productId: ID!) {
                ProductUpdatesReadModel(id: $productId) {
                  id
                }
              }
            `,
          })

          await expect(queryPromise).to.eventually.be.rejectedWith(/jwt malformed/)
        })
      })

      it('when using a client with subscriptions, it gets the expected error on connect', async () => {
        const connectionPromise = new Promise(async (resolve, reject) => {
          await graphQLClientWithSubscriptions('ABC', (err) => {
            if (err) {
              reject(err)
            } else {
              resolve()
            }
          })
        })
        await expect(connectionPromise).to.eventually.be.rejectedWith(/jwt malformed/)
      })
    })

    context('with a valid token', () => {
      let authToken: string
      let client: DisconnectableApolloClient

      before(async () => {
        authToken = await getTokenForUser(internet.email(), 'UserWithEmail')
        client = await graphQLClientWithSubscriptions(() => authToken)
      })

      after(() => {
        client.disconnect()
      })

      it('can submit a secured command they have privileges for', async () => {
        const mutationResult = await client.mutate({
          mutation: gql`
            mutation {
              CreateProduct(
                input: {
                  sku: "314"
                  displayName: "Something fancy"
                  description: "It's really fancy"
                  priceInCents: 4000
                  currency: "EUR"
                }
              )
            }
          `,
        })

        expect(mutationResult).not.to.be.null
        expect(mutationResult.data.CreateProduct).to.be.true
      })

      it('can query a secured read model they have privileges for', async () => {
        const mockSku = random.alphaNumeric(random.number({ min: 10, max: 20 }))
        const mockDisplayName = lorem.sentence()
        const mockDescription = lorem.paragraph()
        const mockPriceInCents = random.number({ min: 1 })
        const mockCurrency = finance.currencyCode()

        // Create a product
        await client.mutate({
          variables: {
            sku: mockSku,
            displayName: mockDisplayName,
            description: mockDescription,
            priceInCents: mockPriceInCents,
            currency: mockCurrency,
          },
          mutation: gql`
            mutation CreateProduct(
              $sku: String
              $displayName: String
              $description: String
              $priceInCents: Float
              $currency: String
            ) {
              CreateProduct(
                input: {
                  sku: $sku
                  displayName: $displayName
                  description: $description
                  priceInCents: $priceInCents
                  currency: $currency
                }
              )
            }
          `,
        })

        // Query the product
        const result = await waitForIt(
          () => {
            return client.query({
              query: gql`
                query {
                  ProductReadModels {
                    id
                    sku
                    displayName
                    description
                    price
                    availability
                    deleted
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ProductReadModels?.some((product: any) => product.sku === mockSku)
        )

        const product = result.data.ProductReadModels.find((product: any) => product.sku === mockSku)
        const productId = product.id

        const expectedProduct = {
          __typename: 'ProductReadModel',
          sku: mockSku,
          id: productId,
          description: mockDescription,
          displayName: mockDisplayName,
          availability: 0,
          deleted: false,
          price: {
            cents: mockPriceInCents,
            currency: mockCurrency,
          },
        }
        expect(product).not.to.be.null
        expect(product).to.be.deep.equal(expectedProduct)
      })

      it('can subscribe to a secured read model they have privileges for', async () => {
        const currentSubscriptionsCount = await countSubscriptionsItems()

        // We check that we receive data after modifying the read model with a command
        const subscription = await client.subscribe({
          query: gql`
            subscription {
              ProductReadModels {
                id
                sku
                description
                displayName
              }
            }
          `,
        })

        const subscriptionPromise = new Promise((resolve, reject) => {
          subscription.subscribe({
            // This "subscribe" is the one of the Observable returned by Apollo
            next: resolve,
            error: reject,
          })
        })

        await waitForIt(
          () => countSubscriptionsItems(),
          (count: number) => count > currentSubscriptionsCount
        )

        await client.mutate({
          variables: {
            productSKU: mockProductSKU,
          },
          mutation: gql`
            mutation CreateProduct($productSKU: String!) {
              CreateProduct(
                input: {
                  sku: $productSKU
                  displayName: "Something fancy"
                  description: "It's really fancy"
                  priceInCents: 4000
                  currency: "EUR"
                }
              )
            }
          `,
        })

        await expect(subscriptionPromise).to.eventually.be.fulfilled
      })

      it("can't send a command they don't have privileges for", async () => {
        const mutationPromise = client.mutate({
          variables: {
            productId: mockProductId,
          },
          mutation: gql`
            mutation DeleteProduct($productId: ID!) {
              DeleteProduct(input: { productId: $productId })
            }
          `,
        })

        await expect(mutationPromise).to.eventually.be.rejectedWith("Access denied for command 'DeleteProduct'")
      })

      it("can't query a read model they don't have privileges for", async () => {
        const queryPromise = client.query({
          variables: {
            productId: mockProductId,
          },
          query: gql`
            query ProductUpdatesReadModel($productId: ID!) {
              ProductUpdatesReadModel(id: $productId) {
                id
                availability
                lastUpdate
                previousUpdate
              }
            }
          `,
        })

        await expect(queryPromise).to.eventually.be.rejectedWith('Access denied for read model ProductUpdatesReadMode')
      })

      it("can't subscribe to a read model they don't have privileges for", async () => {
        const subscription = await client.subscribe({
          query: gql`
            subscription {
              ProductUpdatesReadModels {
                id
              }
            }
          `,
        })

        const subscriptionPromise = new Promise((_, reject) => {
          subscription.subscribe({
            // This "subscribe" is the one of the Observable returned by Apollo
            error: reject,
          })
        })

        await expect(subscriptionPromise).to.eventually.be.rejectedWith(
          /Access denied for read model ProductUpdatesReadModel/
        )
      })
    })
  })

  // The Admin role is configured in the test project to forbid sign ups
  context('someone with an admin account', () => {
    context('with a valid token', () => {
      let authToken: string
      let client: DisconnectableApolloClient

      before(async () => {
        authToken = await getTokenForUser(internet.email(), 'Admin')
        client = await graphQLClientWithSubscriptions(() => authToken)
      })

      after(() => {
        client.disconnect()
      })

      it('can query a read model they have privileges for', async () => {
        const queryResult = await client.query({
          variables: {
            productId: mockProductId,
          },
          query: gql`
            query ProductUpdatesReadModel($productId: ID!) {
              ProductUpdatesReadModel(id: $productId) {
                id
                availability
              }
            }
          `,
        })

        expect(queryResult).not.to.be.null // It's enough that the query wasn't rejected
      })

      it('can send a command they have privileges for', async () => {
        const mutationResult = await client.mutate({
          variables: {
            productId: mockProductId,
          },
          mutation: gql`
            mutation DeleteProduct($productId: ID!) {
              DeleteProduct(input: { productId: $productId })
            }
          `,
        })

        expect(mutationResult).not.to.be.null
        expect(mutationResult.data.DeleteProduct).to.be.true
      })

      it('can subscribe to a read model they have privileges for', async () => {
        const currentSubscriptionsCount = await countSubscriptionsItems()

        // We check that we receive data after modifying the read model with a command
        const subscription = await client.subscribe({
          query: gql`
            subscription {
              ProductUpdatesReadModels {
                id
                availability
              }
            }
          `,
        })

        const subscriptionPromise = new Promise((resolve, reject) => {
          subscription.subscribe({
            // This "subscribe" is the one of the Observable returned by Apollo
            next: resolve,
            error: reject,
          })
        })

        await waitForIt(countSubscriptionsItems, (count) => count > currentSubscriptionsCount)

        await client.mutate({
          variables: {
            productSKU: mockProductSKU,
          },
          mutation: gql`
            mutation CreateProduct($productSKU: String!) {
              CreateProduct(
                input: {
                  sku: $productSKU
                  displayName: "Something fancy"
                  description: "It's really fancy"
                  priceInCents: 4000
                  currency: "EUR"
                }
              )
            }
          `,
        })

        await expect(subscriptionPromise).to.eventually.be.fulfilled
      })
    })
  })
})
