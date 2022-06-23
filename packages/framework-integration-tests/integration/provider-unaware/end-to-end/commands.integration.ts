import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { commerce, finance, random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { applicationUnderTest } from './setup'

describe('Commands end-to-end tests', () => {
  context('with public commands', () => {
    let client: ApolloClient<NormalizedCacheObject>

    before(async () => {
      client = applicationUnderTest.graphql.client()
    })

    it('accepts a command successfully', async () => {
      const response = await client.mutate({
        variables: {
          cartId: random.uuid(),
          productId: random.uuid(),
          quantity: random.number({ min: 1 }),
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
      })

      expect(response).not.to.be.null
      expect(response?.data?.ChangeCartItem).to.be.true
    })

    it('accepts an empty command', async () => {
      const response = await client.mutate({
        variables: {},
        mutation: gql`
          mutation {
            EmptyCommand
          }
        `,
      })

      expect(response).not.to.be.null
      expect(response?.data?.EmptyCommand).to.be.equal('Empty command executed')
    })
  })

  context('when the command requires a specific role', () => {
    it('rejects the command if the user does not have the required role', async () => {
      const authToken = applicationUnderTest.token.forUser('admin@example.com', 'User')
      const client = applicationUnderTest.graphql.client(authToken)
      const result = await client.mutate({
        variables: {
          sku: random.uuid(),
          displayName: commerce.product(),
          description: commerce.productDescription(),
          priceInCents: Math.floor(Math.random() * 100 + 1),
          currency: finance.currencyName(),
        },
        mutation: gql`
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
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

      expect(result).not.to.be.null
      expect(result?.data?.CreateProduct).to.be.false
    })

    it('accepts the command if the user has the required role', async () => {
      const authToken = applicationUnderTest.token.forUser('admin@example.com', 'Admin')
      const client = applicationUnderTest.graphql.client(authToken)
      const result = await client.mutate({
        variables: {
          sku: random.uuid(),
          displayName: commerce.product(),
          description: commerce.productDescription(),
          priceInCents: Math.floor(Math.random() * 100 + 1),
          currency: finance.currencyName(),
        },
        mutation: gql`
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
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

      expect(result).not.to.be.null
      expect(result?.data?.CreateProduct).to.be.true
    })
  })

  context('when the command requires a custom authorization policy', () => {
    it('rejects the command when the policy is not satisfied', async () => {
      const authToken = applicationUnderTest.token.forUser('logger@example.com', 'User')
      const client = applicationUnderTest.graphql.client(authToken)
      const result = await client.mutate({
        variables: {
          sku: random.uuid(),
          displayName: commerce.product(),
          description: commerce.productDescription(),
          priceInCents: Math.floor(Math.random() * 100 + 1),
          currency: finance.currencyName(),
        },
        mutation: gql`
          mutation LogSomething(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
          ) {
            LogSomething(
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

      expect(result).not.to.be.null
      expect(result?.data?.LogSomething).to.be.false
    })

    it('accepts the command when the policy is satisfied', async () => {
      const authToken = applicationUnderTest.token.forUser('logger@example.com', 'User', { customClaims: { canLogSomething: true } })
      const client = applicationUnderTest.graphql.client(authToken)
      const result = await client.mutate({
        variables: {
          sku: random.uuid(),
          displayName: commerce.product(),
          description: commerce.productDescription(),
          priceInCents: Math.floor(Math.random() * 100 + 1),
          currency: finance.currencyName(),
        },
        mutation: gql`
          mutation LogSomething(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
          ) {
            LogSomething(
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

      expect(result).not.to.be.null
      expect(result?.data?.LogSomething).to.be.true
    })
  })
})
