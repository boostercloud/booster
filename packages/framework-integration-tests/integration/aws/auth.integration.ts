import { graphQLClient, signUpURL, authClientID, signInURL, confirmUser } from './utils'
import gql from 'graphql-tag'
import { expect } from 'chai'
import fetch from 'cross-fetch'

describe('With the auth API', () => {
  context('an internet rando', () => {
    xit("can't submit a secured command", async () => {
      const client = await graphQLClient()

      const response = client.mutate({
        mutation: gql`
          mutation {
            CreateProduct(input: {
              product: {
                id: '42f33598-75ba-4e76-b728-20144c8b74e8',
                sku: 'P-42',
                displayName: 'Yoga pants',
                description: 'Pretty standard pair of yoga pants with not much to highlight in particular.',
                price: {
                  cents: 2000,
                  currency: 'EUR'
                },
                pictures: [],
                deleted: false
              }
            })
          }
        `,
      })

      // TODO: This is currently being rejected for the wrong causes,
      // we must revisit this once GraphQL errors are fixed and work on Apollo Client
      // eslint-disable-next-line @typescript-eslint/no-floating-promises
      expect(response).to.have.been.rejected
    })

    it("can't query a secured read model")
    it('can submit a public command')
    it('can query a public read model')

    it('can sign up for a user account', async () => {
      const url = await signUpURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          clientId: clientId,
          username: 'Su_morenito_19@example.com', // Why this user name? Reasons: https://youtu.be/h6k5qbt72Os
          password: 'Flama_69',
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
          username: 'admin@example.com',
          password: 'Flama_69',
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
  context('someone with a user account', async () => {
    // We'll confirm here the user account created in the previous test
    await confirmUser('Su_morenito_19@example.com')
    let accessToken: string

    it('can sign in their account and get a valid token', async () => {
      const url = await signInURL()
      const clientId = await authClientID()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          clientId: clientId,
          username: 'Su_morenito_19@example.com', // Why this user name? Reasons: https://youtu.be/h6k5qbt72Os
          password: 'Flama_69',
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
      accessToken = message.accessToken
      console.log('Yay! Got a User access token! ' + accessToken)
    })

    it('can submit a secured command they have privileges for')
    it('can query a secured command they have privileges for')
    it("can't send a command they don't have privileges for")
    it("can't query a read model they don't have privileges for")
  })

  // The Admin role is configured in the test project to forbid sign ups
  context('someone with an admin account', () => {
    it('can sign in their account and get a valid token')
  })
})
