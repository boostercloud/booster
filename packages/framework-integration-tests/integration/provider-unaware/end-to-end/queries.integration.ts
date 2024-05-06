import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
import { random } from 'faker'
import { expect } from '../../helper/expect'
import { applicationUnderTest } from './setup'
import { waitForIt } from '../../helper/sleep'
import { UUID } from '@boostercloud/framework-types'
import { beforeHookQueryID, beforeHookQueryMultiply } from '../../../src/constants'

describe('Queries end-to-end tests', () => {
  context('with public queries', () => {
    let client: ApolloClient<NormalizedCacheObject>

    before(async () => {
      client = applicationUnderTest.graphql.client()
    })

    it('accepts a query successfully', async () => {
      const cartId = random.uuid()
      const quantity = random.number({ min: 1 })
      await client.mutate({
        variables: {
          cartId: cartId,
          productId: random.uuid(),
          quantity: quantity,
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
      })

      const response = await waitForIt(
        () =>
          client.query({
            variables: {
              cartId: cartId,
            },
            query: gql`
              query CartTotalQuantity($cartId: ID!) {
                CartTotalQuantity(input: { cartId: $cartId })
              }
            `,
          }),
        (result) => result?.data?.CartTotalQuantity != 0
      )

      expect(response).not.to.be.null
      expect(response?.data?.CartTotalQuantity).to.be.eq(quantity)
    })

    it('accepts a query with an object as result', async () => {
      const cartIds: Array<UUID> = []
      const countries = ['spain', undefined, 'india', 'spain']
      // create 4 carts
      for (let i = 0; i < 4; i++) {
        cartIds.push(UUID.generate())
        await client.mutate({
          variables: {
            cartId: cartIds[i],
            productId: random.uuid(),
            quantity: 10,
          },
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
        })

        if (countries[i]) {
          await client.mutate({
            variables: {
              cartId: cartIds[i],
              address: {
                firstName: 'firstName',
                lastName: 'lastName',
                country: countries[i],
                state: 'state',
                postalCode: '11111',
                address: 'address',
              },
              quantity: 10,
            },
            mutation: gql`
              mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
                UpdateShippingAddress(input: { cartId: $cartId, address: $address })
              }
            `,
          })
        }
      }

      const response = await waitForIt(
        () =>
          client.query({
            variables: {},
            query: gql`
              query CartsByCountry {
                CartsByCountry
              }
            `,
          }),
        (result) => result?.data?.CartsByCountry?.length !== 0 && result?.data?.CartsByCountry['spain']?.length === 2
      )

      expect(response).not.to.be.null
      expect(response?.data?.CartsByCountry['spain'].length).to.be.eq(2)
      expect(response?.data?.CartsByCountry['india'].length).to.be.eq(1)
    })

    it('before hook multiply the value by beforeHookQueryMultiply', async () => {
      const cartId = beforeHookQueryID
      const quantity = random.number({ min: 1 })
      await client.mutate({
        variables: {
          cartId: cartId,
          productId: random.uuid(),
          quantity: quantity,
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
      })

      const response = await waitForIt(
        () =>
          client.query({
            variables: {
              cartId: cartId,
            },
            query: gql`
              query CartTotalQuantity($cartId: ID!) {
                CartTotalQuantity(input: { cartId: $cartId })
              }
            `,
          }),
        (result) => result?.data?.CartTotalQuantity != 0
      )

      expect(response).not.to.be.null
      expect(response?.data?.CartTotalQuantity).to.be.eq(beforeHookQueryMultiply * quantity)
    })
  })

  context('when the query requires a specific role', () => {
    it('rejects the command if the user does not have the required role', async () => {
      const authToken = applicationUnderTest.token.forUser('admin@example.com', 'User')
      const client = applicationUnderTest.graphql.client(authToken)
      const resultPromise = client.mutate({
        variables: {},
        mutation: gql`
          query CartWithRole {
            CartWithRole
          }
        `,
      })

      await expect(resultPromise).to.be.eventually.rejectedWith(/Access denied for this resource/)
    })

    it('accepts the command if the user has the required role', async () => {
      const authToken = applicationUnderTest.token.forUser('admin@example.com', 'Admin')
      const client = applicationUnderTest.graphql.client(authToken)
      const result = await client.mutate({
        variables: {},
        mutation: gql`
          query CartWithRole {
            CartWithRole
          }
        `,
      })

      expect(result).not.to.be.null
      expect(result?.data?.CartWithRole).to.be.true
    })
  })
})
