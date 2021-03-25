import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { createUser, getUserAuthInformation, graphQLClient, UserAuthInformation } from '../providers/aws/utils'
import { random, commerce, finance, lorem, internet } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { sleep, waitForIt } from '../helper/sleep'

describe('Entities end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  let userAuthInformation: UserAuthInformation
  let userEmail: string
  const mockPassword = 'Enable_G0d_Mode3e!'

  before(async () => {
    userEmail = internet.email()
    // TODO: Make retrieval of auth token cloud agnostic
    await createUser(userEmail, mockPassword, 'UserWithEmail')
    userAuthInformation = await getUserAuthInformation(userEmail, mockPassword)
    client = await graphQLClient(userAuthInformation.idToken)
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
      await createUser(adminEmail, mockPassword, 'Admin')
      const adminUserAuthInformation = await getUserAuthInformation(adminEmail, mockPassword)
      client = await graphQLClient(adminUserAuthInformation.idToken)

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

      console.log('Waiting 1 second for deletion to complete...')
      await sleep(1000)

      client = await graphQLClient(userAuthInformation.idToken)
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
