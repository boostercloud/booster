import {
  graphQLClientWithSubscriptions,
  signUpURL,
  authClientID,
  signInURL,
  confirmUser,
  createUser,
  deleteUser,
  waitForIt,
  createPassword,
  getAuthToken,
  DisconnectableApolloClient,
} from './utils'
import gql from 'graphql-tag'
import { expect } from 'chai'
import fetch from 'cross-fetch'
import * as chai from 'chai'
import { random, internet, lorem, finance } from 'faker'

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
      const subscription = await client.subscribe({
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

      // We check that we receive data after modifying the read model with a command
      const subscriptionPromise = new Promise((resolve, reject) => {
        subscription.subscribe({
          // This "subscribe" is the one of the Observable returned by Apollo
          next: resolve,
          error: reject,
        })
      })

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

    it('can sign up for a user account', async () => {
      const userEmail = internet.email()
      const userPassword = createPassword()

      const url = await signUpURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          clientId: clientId,
          username: userEmail,
          password: userPassword,
          userAttributes: {
            roles: ['User'],
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).to.be.empty

      expect(response.status).to.equal(200)
    })

    it("can't sign up for an admin account", async () => {
      const adminEmail = internet.email()
      const adminPassword = createPassword()

      const url = await signUpURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'post',
        body: JSON.stringify({
          clientId: clientId,
          username: adminEmail,
          password: adminPassword,
          userAttributes: {
            roles: ['Admin'],
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).not.to.be.empty
      expect(message.message).to.match(/PreSignUp failed with error User with role Admin can't sign up by themselves/)

      expect(response.status).to.equal(400)
    })
  })

  // The User role is configured in the test project to allow sign ups
  context('someone with a user account', () => {
    let userEmail: string
    let userPassword: string

    before(async () => {
      userEmail = internet.email()
      userPassword = createPassword()

      // Create user
      const url = await signUpURL()
      const clientId = await authClientID()
      await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          clientId: clientId,
          username: userEmail,
          password: userPassword,
          userAttributes: {
            roles: ['User'],
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      // Confirm user
      await confirmUser(userEmail)
    })
    after(async () => {
      await deleteUser(userEmail)
    })

    it('can sign in their account and get a valid token', async () => {
      const url = await signInURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          clientId: clientId,
          username: userEmail,
          password: userPassword,
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      expect(response.status).to.equal(200)

      const message = await response.json()
      expect(message).not.to.be.empty
      expect(message.accessToken).not.to.be.empty
    })

    context('with a signed-in user', () => {
      let client: DisconnectableApolloClient

      before(async () => {
        client = await graphQLClientWithSubscriptions(await getAuthToken(userEmail, userPassword))
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
        const subscription = await client.subscribe({
          query: gql`
            subscription {
              ProductReadModels {
                id
                sku
              }
            }
          `,
        })

        // We check that we receive data after modifying the read model with a command
        const subscriptionPromise = new Promise((resolve, reject) => {
          subscription.subscribe({
            // This "subscribe" is the one of the Observable returned by Apollo
            next: resolve,
            error: reject,
          })
        })

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
        const subscriptionPromise = new Promise((_, reject) => {
          client
            .subscribe({
              query: gql`
                subscription {
                  ProductUpdatesReadModels {
                    id
                  }
                }
              `,
            })
            .subscribe({
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
    let adminEmail: string
    let adminPassword: string

    before(async () => {
      adminEmail = internet.email()
      adminPassword = createPassword()

      // Create admin user
      await createUser(adminEmail, adminPassword, 'Admin')
    })
    after(async () => {
      await deleteUser(adminEmail)
    })

    it('can sign in their account and get a valid token', async () => {
      const url = await signInURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          clientId: clientId,
          username: adminEmail,
          password: adminPassword,
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      expect(response.status).to.equal(200)

      const message = await response.json()
      expect(message).not.to.be.empty
      expect(message.accessToken).not.to.be.empty
    })

    context('with a signed-in admin user', () => {
      let client: DisconnectableApolloClient

      before(async () => {
        client = await graphQLClientWithSubscriptions(await getAuthToken(adminEmail, adminPassword))
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

        // We check that we receive data after modifying the read model with a command
        const subscriptionPromise = new Promise((resolve, reject) => {
          subscription.subscribe({
            // This "subscribe" is the one of the Observable returned by Apollo
            next: resolve,
            error: reject,
          })
        })

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
