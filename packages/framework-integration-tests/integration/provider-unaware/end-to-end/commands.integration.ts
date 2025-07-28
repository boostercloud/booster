import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
import { commerce, finance, random } from 'faker'
import { expect } from '../../helper/expect'
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
      const resultPromise = client.mutate({
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

      await expect(resultPromise).to.be.eventually.rejectedWith(/Access denied for this resource/)
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
      const resultPromise = client.mutate({
        variables: {
          sku: random.uuid(),
          displayName: commerce.product(),
          description: commerce.productDescription(),
          priceInCents: Math.floor(Math.random() * 100 + 1),
          currency: finance.currencyName(),
        },
        mutation: gql`
          mutation {
            LogSomething
          }
        `,
      })

      await expect(resultPromise).to.be.eventually.rejectedWith(/You are not allowed to log something/)
    })

    it('accepts the command when the policy is satisfied', async () => {
      const authToken = applicationUnderTest.token.forUser('logger@example.com', undefined, {
        customClaims: { canLogSomething: true },
      })
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
          mutation {
            LogSomething
          }
        `,
      })

      expect(result).not.to.be.null
      expect(result?.data?.LogSomething).to.be.true
    })
  })

  context('with migration commands', () => {
    let client: ApolloClient<NormalizedCacheObject>

    before(async () => {
      client = applicationUnderTest.graphql.client()
    })

    it('successfully migrates all read models for a specific read model', async () => {
      const response = await client.mutate({
        variables: {
          readModelName: 'CartReadModel',
        },
        mutation: gql`
          mutation MigrateAllReadModel($readModelName: String!) {
            MigrateAllReadModel(input: { readModelName: $readModelName })
          }
        `,
      })

      expect(response).not.to.be.null
      expect(response?.data?.MigrateAllReadModel).to.be.a('string')
      expect(response?.data?.MigrateAllReadModel).to.include('Migrated')
      expect(response?.data?.MigrateAllReadModel).to.include('CartReadModel')
    })

    it('successfully migrates all read models for a different read model', async () => {
      const response = await client.mutate({
        variables: {
          readModelName: 'ProductReadModel',
        },
        mutation: gql`
          mutation MigrateAllReadModel($readModelName: String!) {
            MigrateAllReadModel(input: { readModelName: $readModelName })
          }
        `,
      })

      expect(response).not.to.be.null
      expect(response?.data?.MigrateAllReadModel).to.be.a('string')
      expect(response?.data?.MigrateAllReadModel).to.include('Migrated')
      expect(response?.data?.MigrateAllReadModel).to.include('ProductReadModel')
    })

    it('fails when trying to migrate a non-existent read model', async () => {
      const resultPromise = client.mutate({
        variables: {
          readModelName: 'NonExistentReadModel',
        },
        mutation: gql`
          mutation MigrateAllReadModel($readModelName: String!) {
            MigrateAllReadModel(input: { readModelName: $readModelName })
          }
        `,
      })

      await expect(resultPromise).to.be.eventually.rejectedWith(/Resource Not Found/)
    })

    it('successfully runs general data migrations', async () => {
      const response = await client.mutate({
        variables: {},
        mutation: gql`
          mutation {
            MigrateCommand
          }
        `,
      })

      expect(response).not.to.be.null
      // MigrateCommand doesn't return a value, so we just check it doesn't throw
      expect(response?.data?.MigrateCommand).to.not.be.undefined
    })
  })
})
