import * as chai from 'chai'
import { random, internet, phone, lorem, finance } from 'faker'
import {
  DisconnectableApolloClient,
  graphQLClientWithSubscriptions,
  waitForIt,
  createPassword,
  countSubscriptionsItems,
  signUpURL,
  confirmUser,
  deleteUser,
  getUserAuthInformation,
  graphQLClient,
  refreshUserAuthInformation,
  signInURL,
  UserAuthInformation,
  createUser,
} from './helpers/utils'
import gql from 'graphql-tag'
import fetch from 'cross-fetch'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import ApolloClient from 'apollo-client'

chai.use(require('chai-as-promised'))
const expect = chai.expect

describe('With the auth API', () => {
  let mockProductId: string
  let mockCartId: string
  let mockProductSKU: string

  beforeEach(() => {
    mockProductId = random.uuid()
    mockCartId = random.uuid()
    mockProductSKU = random.uuid()
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

    it('can sign up with an email', async () => {
      const userEmail = internet.email()
      const userPassword = createPassword()

      const url = await signUpURL()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          username: userEmail,
          password: userPassword,
          userAttributes: {
            role: 'UserWithEmail',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).not.to.be.empty

      expect(response.status).to.equal(200)
    })

    it('can sign up with a phone number', async () => {
      const userPhoneNumber = phone.phoneNumber('+1##########')
      const userPassword = createPassword()

      const url = await signUpURL()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          username: userPhoneNumber,
          password: userPassword,
          userAttributes: {
            role: 'SuperUser',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).not.to.be.empty

      expect(response.status).to.equal(200)
    })

    it("can't sign up for an admin account", async () => {
      const adminEmail = internet.email()
      const adminPassword = createPassword()

      const url = await signUpURL()

      const response = await fetch(url, {
        method: 'post',
        body: JSON.stringify({
          username: adminEmail,
          password: adminPassword,
          userAttributes: {
            role: 'Admin',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).not.to.be.empty
      //expect(message.message).to.match(/PreSignUp failed with error User with role Admin can't sign up by themselves/)

      expect(response.status).to.equal(400)
    })

    it("can't sign up with an email if specified role only has phone as sign up option", async () => {
      const userEmail = internet.email()
      const userPassword = createPassword()

      const url = await signUpURL()

      const response = await fetch(url, {
        method: 'post',
        body: JSON.stringify({
          username: userEmail,
          password: userPassword,
          userAttributes: {
            role: 'UserWithPhone',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).not.to.be.empty
      //expect(message.message).to.match(/PreSignUp failed with error User with role UserWithPhone can't sign up with an email, a phone number is expected./)

      expect(response.status).to.equal(400)
    })

    it("can't sign up with a phone number if specified role only has email as sign up option", async () => {
      const userPhoneNumber = phone.phoneNumber('+1##########')
      const userPassword = createPassword()

      const url = await signUpURL()

      const response = await fetch(url, {
        method: 'post',
        body: JSON.stringify({
          username: userPhoneNumber,
          password: userPassword,
          userAttributes: {
            role: 'UserWithEmail',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const message = await response.json()
      expect(message).not.to.be.empty
      expect(response.status).to.equal(400)
    })
  })

  context('someone with a user with email account', () => {
    let userEmail: string
    let anotherUserEmail: string
    let userPassword: string

    before(async () => {
      userEmail = internet.email()
      userPassword = createPassword()

      // Create user with confirmation
      const url = await signUpURL()

      await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          username: userEmail,
          password: userPassword,
          userAttributes: {
            role: 'UserWithEmail',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      // Create user without confirmation
      anotherUserEmail = internet.email()
      const urlNoConfirmation = await signUpURL()

      await fetch(urlNoConfirmation, {
        method: 'POST',
        body: JSON.stringify({
          username: anotherUserEmail,
          password: userPassword,
          userAttributes: {
            role: 'SuperUserNoConfirmation',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })
    })

    after(async () => {
      await deleteUser(userEmail)
    })

    it('can sign in their account when skipConfirmation is false and user is manually confirmed. User will get a valid token.', async () => {
      // Manually confirming user
      await confirmUser(userEmail)

      const url = await signInURL()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
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

    it('can sign in their account without manually confirming user when skipConfirmation is true. User will get a valid token.', async () => {
      const url = await signInURL()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
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
    })

    context('with a signed-in user', () => {
      let userAuthInformation: UserAuthInformation
      let authToken: string
      let client: DisconnectableApolloClient

      before(async () => {
        userAuthInformation = await getUserAuthInformation(userEmail, userPassword)
        authToken = userAuthInformation.idToken
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

      describe('after refreshing the token', () => {
        let refreshedUserAuthInformation: UserAuthInformation

        before(async () => {
          refreshedUserAuthInformation = await refreshUserAuthInformation(userAuthInformation.refreshToken)
          // Update access token that's being used by the Apollo client
          authToken = refreshedUserAuthInformation.idToken
          await client.reconnect()
        })

        it('should return a new access token', () => {
          expect(userAuthInformation.accessToken).not.to.be.equal(refreshedUserAuthInformation.accessToken)
        })

        it('should have a token that expires in 3600 seconds', () => {
          expect(refreshedUserAuthInformation.expiresIn).to.be.equal(3600)
        })

        it('should have a Bearer token type', () => {
          expect(refreshedUserAuthInformation.tokenType).to.be.equal('Bearer')
        })

        it('should return an empty refresh token', () => {
          expect(refreshedUserAuthInformation.refreshToken).to.be.empty
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
      })
    })
  })

  context('someone with a user with phone number account', () => {
    let userPhoneNumber: string
    let userPhoneNumberNoConfirmation: string
    let userPassword: string

    before(async () => {
      userPhoneNumber = phone.phoneNumber('+1##########')
      userPassword = createPassword()

      // Create user with confirmation required
      const url = await signUpURL()

      await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          username: userPhoneNumber,
          password: userPassword,
          userAttributes: {
            role: 'UserWithPhone',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })

      // Create user with confirmation required
      userPhoneNumberNoConfirmation = phone.phoneNumber('+1##########')

      const urlNoConfirmation = await signUpURL()

      await fetch(urlNoConfirmation, {
        method: 'POST',
        body: JSON.stringify({
          username: userPhoneNumberNoConfirmation,
          password: userPassword,
          userAttributes: {
            role: 'SuperUserNoConfirmation',
          },
        }),
        headers: {
          'Content-Type': 'application/json',
        },
      })
    })

    after(async () => {
      await deleteUser(userPhoneNumber)
    })

    it('can sign in their account when skipConfirmation is false and user is manually confirmed. User will get a valid token.', async () => {
      // Manually confirming user
      await confirmUser(userPhoneNumber)

      const url = await signInURL()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          username: userPhoneNumber,
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

    it('can sign in their account without manually confirming user when skipConfirmation is true. User will get a valid token.', async () => {
      const url = await signInURL()

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
          username: userPhoneNumberNoConfirmation,
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
  })

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

      const response = await fetch(url, {
        method: 'POST',
        body: JSON.stringify({
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
      let adminUserAuthInformation: UserAuthInformation
      let authToken: string
      let client: DisconnectableApolloClient

      before(async () => {
        adminUserAuthInformation = await getUserAuthInformation(adminEmail, adminPassword)
        authToken = adminUserAuthInformation.idToken
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

      describe('after refreshing the token', () => {
        let refreshedUserAuthInformation: UserAuthInformation

        before(async () => {
          refreshedUserAuthInformation = await refreshUserAuthInformation(adminUserAuthInformation.refreshToken)
          // Update access token that's being used by the Apollo client
          authToken = refreshedUserAuthInformation.idToken
          await client.reconnect()
        })

        it('should return a new access token', () => {
          expect(adminUserAuthInformation.accessToken).not.to.be.equal(refreshedUserAuthInformation.accessToken)
        })

        it('should have a token that expires in 3600 seconds', () => {
          expect(refreshedUserAuthInformation.expiresIn).to.be.equal(3600)
        })

        it('should have a Bearer token type', () => {
          expect(refreshedUserAuthInformation.tokenType).to.be.equal('Bearer')
        })

        it('should return an empty refresh token', () => {
          expect(refreshedUserAuthInformation.refreshToken).to.be.empty
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
})
