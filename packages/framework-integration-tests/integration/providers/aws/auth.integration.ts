import {
  graphQLClient,
  signUpURL,
  authClientID,
  signInURL,
  confirmUser,
  createUser,
  deleteUser,
  waitForIt,
  createPassword,
  getAuthToken,
} from './utils'
import gql from 'graphql-tag'
import { expect } from 'chai'
import fetch from 'cross-fetch'
import * as chai from 'chai'
import { random, internet, lorem, finance } from 'faker'

chai.use(require('chai-as-promised'))

describe('With the auth API', () => {
  let mockProductId: string
  let mockCartId: string

  before(() => {
    mockProductId = random.uuid()
    mockCartId = random.uuid()
  })

  context('an internet rando', () => {
    it("can't submit a secured command", async () => {
      const client = await graphQLClient()

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
      const client = await graphQLClient()

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

    it('can submit a public command', async () => {
      const client = await graphQLClient()

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

      const client = await graphQLClient()

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
    let userAccessToken: string

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

      // We save the access token for next tests
      userAccessToken = message.accessToken
    })

    it('can submit a secured command they have privileges for', async () => {
      const client = await graphQLClient(userAccessToken)

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

      userAccessToken = await getAuthToken(userEmail, userPassword)
      const client = await graphQLClient(userAccessToken)

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

      // Wait for product to be available in read model
      const products = await waitForIt(
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

      const product = products.data.ProductReadModels.find((product: any) => product.sku === mockSku)
      const productId = product.id

      // Query just created product
      const queryResult = await client.query({
        variables: {
          productId: productId,
        },
        query: gql`
          query ProductReadModel($productId: ID!) {
            ProductReadModel(id: $productId) {
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
      expect(queryResult).not.to.be.null
      const queryProduct = queryResult.data.ProductReadModel
      expect(queryProduct).to.be.deep.equal(expectedProduct)
    })

    it("can't send a command they don't have privileges for", async () => {
      userAccessToken = await getAuthToken(userEmail, userPassword)
      const client = await graphQLClient(userAccessToken)

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
      userAccessToken = await getAuthToken(userEmail, userPassword)
      const client = await graphQLClient(userAccessToken)

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
  })

  // The Admin role is configured in the test project to forbid sign ups
  context('someone with an admin account', () => {
    let adminEmail: string
    let adminPassword: string
    let adminAccessToken: string

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

      // We save the access token for next tests
      adminAccessToken = message.accessToken
    })

    it('can query a read model they have privileges for', async () => {
      adminAccessToken = await getAuthToken(adminEmail, adminPassword)
      const client = await graphQLClient(adminAccessToken)

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
      adminAccessToken = await getAuthToken(adminEmail, adminPassword)
      const client = await graphQLClient(adminAccessToken)

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
  })
})
