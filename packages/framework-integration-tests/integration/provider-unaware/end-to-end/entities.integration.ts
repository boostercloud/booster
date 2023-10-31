import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
import { random, commerce, finance, lorem, internet } from 'faker'
import { expect } from 'chai'
import { sleep, waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'
import { UUID } from '@boostercloud/framework-types'
import { NEW_CART_IDS, QUANTITY_AFTER_DATA_MIGRATION_V2, QUANTITY_TO_MIGRATE_DATA } from '../../../src/constants'
import { ProductType } from '../../../src/entities/product'

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
    let mockProductType: ProductType
    let mockProductDetails

    let productId: string

    beforeEach(async () => {
      mockSku = random.uuid()
      mockDisplayName = commerce.productName()
      mockDescription = lorem.paragraph()
      mockPriceInCents = random.number({ min: 1 })
      mockCurrency = finance.currencyCode()
      mockProductType = 'Clothing'
      mockProductDetails = {
        color: commerce.color(),
        material: commerce.productMaterial(),
      }

      // Add one item
      await client.mutate({
        variables: {
          sku: mockSku,
          displayName: mockDisplayName,
          description: mockDescription,
          priceInCents: mockPriceInCents,
          currency: mockCurrency,
          productDetails: mockProductDetails,
          productType: mockProductType,
        },
        mutation: gql`
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
            $productDetails: JSON
            $productType: JSON
          ) {
            CreateProduct(
              input: {
                sku: $sku
                displayName: $displayName
                description: $description
                priceInCents: $priceInCents
                currency: $currency
                productDetails: $productDetails
                productType: $productType
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
                  productDetails
                  productType
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
        productDetails: mockProductDetails,
        productType: mockProductType,
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
                  productDetails
                  productType
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

  context('Data migration', () => {
    //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
    if (process.env.TESTED_PROVIDER === 'AWS') {
      console.log('****************** Warning **********************')
      console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS')
      console.log('*************************************************')
      return
    }

    context('with different id and values', () => {
      const mockCartCount = 3
      const mockCartItems: Array<UUID> = []

      beforeEach(async () => {
        const changeCartPromises: Array<Promise<unknown>> = []

        for (let i = 0; i < mockCartCount; i++) {
          const mockCartId = random.uuid()
          const mockProductId: string = random.uuid()
          const mockQuantity = QUANTITY_TO_MIGRATE_DATA
          mockCartItems.push(mockCartId)

          changeCartPromises.push(
            client.mutate({
              variables: {
                cartId: mockCartId,
                productId: mockProductId,
                quantity: mockQuantity,
              },
              mutation: gql`
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
                  ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
                }
              `,
            })
          )
        }

        await Promise.all(changeCartPromises)

        await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: {
                  id: { in: [...mockCartItems] },
                },
              },
              query: gql`
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels.items.length == mockCartCount
        )
      })

      it('find migrated entities', async () => {
        await client.mutate({
          mutation: gql`
            mutation MigrateCommand {
              MigrateCommand
            }
          `,
        })

        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: {
                  id: { in: [...NEW_CART_IDS] },
                },
              },
              query: gql`
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const items = result?.data?.ListCartReadModels?.items
            const jsonItems = JSON.stringify(items)
            if (!items || items.length !== mockCartCount) {
              return `Waiting for ${mockCartCount} items. ${jsonItems} `
            }

            if (!allItemsHasQuantity(items, QUANTITY_AFTER_DATA_MIGRATION_V2)) {
              return `Waiting for quantities to be equal to ${QUANTITY_AFTER_DATA_MIGRATION_V2}. ${jsonItems}`
            }

            return true
          }
        )

        const readModels = queryResult.data.ListCartReadModels
        readModels.items.forEach((item: { id: any; cartItems: { quantity: any; productId: any }[] }) =>
          expect(
            item.cartItems[0].quantity,
            `Not matching quantity on cartId ${item.id} and productId: ${item.cartItems[0].productId}`
          ).to.be.eq(QUANTITY_AFTER_DATA_MIGRATION_V2)
        )

        await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: {},
              },
              query: gql`
                query ListDataMigrationsReadModels($filter: ListDataMigrationsReadModelFilter) {
                  ListDataMigrationsReadModels(filter: $filter) {
                    items {
                      id
                      status
                      lastUpdated
                    }
                    count
                    cursor
                  }
                }
              `,
            })
          },
          (result) => {
            const resultReadModels = result?.data?.ListDataMigrationsReadModels
            const count = resultReadModels?.count
            if (count < 2) {
              return `Waiting for at least 2 migrations. Done ${count} migrations. ${JSON.stringify(resultReadModels)}`
            }
            return true
          }
        )
      })
    })
  })

  function allItemsHasQuantity(items: any, quantity: number): boolean {
    return items.every((item: { cartItems: { quantity: number }[] }) => item.cartItems[0].quantity === quantity)
  }
})
