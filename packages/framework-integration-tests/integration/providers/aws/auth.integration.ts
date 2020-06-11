import {
  graphQLClient,
  signUpURL,
  authClientID,
  signInURL,
  confirmUser,
  createAdmin,
  deleteUser,
  waitForIt,
} from './utils'
import gql from 'graphql-tag'
import { expect } from 'chai'
import fetch from 'cross-fetch'
import * as chai from 'chai'
import { random } from 'faker'

chai.use(require('chai-as-promised'))

const userEmail = 'Su_morenito_19@example.com' // Why this user name? Reasons: https://youtu.be/h6k5qbt72Os
const userPassword = 'Flama_69'
const adminEmail = 'admin@example.com'
const adminPassword = 'Enable_G0d_Mode3e!'

/*
 * Note: this test file is designed to be run sequentially from top to bottom, which seems to be the default in mocha.
 * Running the test cases out of order or in isolation could have unexpected results.
 */
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

      await expect(queryPromise).to.eventually.be.rejectedWith('Access denied for read model ProductUpdatesReadMode')
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
      const client = await graphQLClient()

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
      const url = await signUpURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'post',
        body: JSON.stringify({
          clientId: clientId,
          username: adminEmail,
          password: userPassword,
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
    let userAccessToken: string

    before(async () => {
      // We'll confirm here the user account created in the previous test
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
      const client = await graphQLClient(userAccessToken)

      const queryResult = await client.query({
        variables: {
          productId: mockProductId,
        },
        query: gql`
          query ProductReadModel($productId: ID!) {
            ProductReadModel(id: $productId) {
              id
              sku
            }
          }
        `,
      })

      expect(queryResult).not.to.be.null
      // TODO: In the current implementation, commands do not return the Ids of the affected entities.
      // When we do, we could check here that we can get the product created in the previous test
    })

    it("can't send a command they don't have privileges for", async () => {
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
    let adminAccessToken: string

    before(async () => {
      // We create the admin account manually
      await createAdmin(adminEmail, adminPassword)
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
