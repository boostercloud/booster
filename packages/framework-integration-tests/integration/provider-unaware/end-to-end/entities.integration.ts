import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random, commerce, finance, lorem, internet } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { sleep, waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'

const secs = 10

describe('Entities end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>
  let userToken: string

  before(async () => {
    const userEmail = internet.email()
    userToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail')
    client = applicationUnderTest.graphql.client(userToken)
  })

  context('Reducers', () => {
    let mockSku: string
    let mockDisplayName: string
    let mockDescription: string
    let mockPriceInCents: number
    let mockCurrency: string

    let productId: string

    beforeEach(async () => {
      mockSku = random.uuid()
      mockDisplayName = commerce.productName()
      mockDescription = lorem.paragraph()
      mockPriceInCents = random.number({ min: 1 })
      mockCurrency = finance.currencyCode()

      // Add one item
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

      // Check that new product is available in read model
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
                  price {
                    cents
                    currency
                  }
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
      productId = product.id

      const expectedResult = {
        __typename: 'ProductReadModel',
        id: productId,
        sku: mockSku,
        displayName: mockDisplayName,
        description: mockDescription,
        price: {
          __typename: 'Money',
          cents: mockPriceInCents,
          currency: mockCurrency,
        },
        availability: 0,
        deleted: false,
      }

      expect(product).to.be.deep.equal(expectedResult)
    })

    it('should reduce the entity as expected', async () => {
      // TODO: Make retrieval of auth token cloud agnostic
      // provision admin user to delete a product
      const adminEmail: string = internet.email()
      const adminToken = applicationUnderTest.token.forUser(adminEmail, 'Admin')
      client = applicationUnderTest.graphql.client(adminToken)

      // Delete a product given an id
      await client.mutate({
        variables: {
          productId: productId,
        },
        mutation: gql`
          mutation DeleteProduct($productId: ID!) {
            DeleteProduct(input: { productId: $productId })
          }
        `,
      })

      console.log(`Waiting ${secs} second${secs > 1 ? 's' : ''} for deletion to complete...`)
      await sleep(secs * 1000)

      client = applicationUnderTest.graphql.client(userToken)
      // Retrieve updated entity
      const queryResult = await waitForIt(
        () => {
          return client.query({
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
                  price {
                    cents
                    currency
                  }
                  availability
                  deleted
                }
              }
            `,
          })
        },
        () => true
      )

      const productData = queryResult.data.ProductReadModel

      expect(productData).to.be.null
    })
  })
})
