import { graphQLClient, signUpURL, authClientID, signInURL, confirmUser, createAdmin, deleteUser } from './utils'
import gql from 'graphql-tag'
import { expect } from 'chai'
import fetch from 'cross-fetch'
import * as chai from 'chai'

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
  context('an internet rando', () => {
    it("can't submit a secured command", async () => {
      const client = await graphQLClient()

      const mutationPromise = client.mutate({
        mutation: gql`
          mutation {
            CreateProduct(
              input: {
                product: {
                  id: "42f33598-75ba-4e76-b728-20144c8b74e8"
                  sku: "P-42"
                  displayName: "Yoga pants"
                  description: "Pretty standard pair of yoga pants with not much to highlight in particular."
                  price: { cents: 2000, currency: "EUR" }
                  pictures: []
                  deleted: false
                }
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
        query: gql`
          query {
            ProductUpdatesReadModel(id: "42") {
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

    it('can submit a public command')
    it('can query a public read model')

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
      console.log('Yay! Got a User access token! ' + userAccessToken)
    })

    it('can submit a secured command they have privileges for')
    it('can query a secured command they have privileges for')
    it("can't send a command they don't have privileges for")
    it("can't query a read model they don't have privileges for")
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
      console.log('Yay! Got a User access token! ' + adminAccessToken)
    })
  })
})
