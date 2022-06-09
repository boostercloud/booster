import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random, commerce, finance, lorem, internet } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { sleep, waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'
import { UUID } from '@boostercloud/framework-types'
import {
  QUANTITY_AFTER_DATA_MIGRATION,
  QUANTITY_AFTER_DATA_MIGRATION_ID,
  QUANTITY_TO_MIGRATE_DATA,
  QUANTITY_TO_MIGRATE_ID,
} from '../../../src/constants'

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

  context('Data migration', () => {
    //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
    if (process.env.TESTED_PROVIDER === 'AWS') {
      console.log('****************** Warning **********************')
      console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS')
      console.log('*************************************************')
      return
    }

    context('with same id', () => {
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
        const migrated = await waitForIt(
          () => {
            return client.mutate({
              mutation: gql`
                mutation CartDataMigrateCommand {
                  CartDataMigrateCommand
                }
              `,
            })
          },
          (result) => result?.data?.CartDataMigrateCommand !== undefined
        )

        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: {
                  id: { in: [...migrated.data.CartDataMigrateCommand] },
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

        const readModels = queryResult.data.ListCartReadModels
        readModels.items.forEach((item: { cartItems: { quantity: any }[] }) =>
          expect(item.cartItems[0].quantity).to.be.eq(QUANTITY_AFTER_DATA_MIGRATION)
        )
        expect(migrated.data.CartDataMigrateCommand).to.have.members(mockCartItems)
      })
    })

    context('with different id', () => {
      const mockCartCount = 3
      const mockCartItems: Array<UUID> = []

      beforeEach(async () => {
        const changeCartPromises: Array<Promise<unknown>> = []

        for (let i = 0; i < mockCartCount; i++) {
          const mockCartId = random.uuid()
          const mockProductId: string = random.uuid()
          const mockQuantity = QUANTITY_TO_MIGRATE_ID
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
        const migrated = await waitForIt(
          () => {
            return client.mutate({
              mutation: gql`
                mutation CartIdDataMigrateCommand {
                  CartIdDataMigrateCommand
                }
              `,
            })
          },
          (result) => result?.data?.CartIdDataMigrateCommand !== undefined
        )

        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: {
                  id: { in: [...migrated.data.CartIdDataMigrateCommand] },
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

        const readModels = queryResult.data.ListCartReadModels
        readModels.items.forEach((item: { cartItems: { quantity: any }[] }) =>
          expect(item.cartItems[0].quantity).to.be.eq(QUANTITY_AFTER_DATA_MIGRATION_ID)
        )
        expect(migrated.data.CartIdDataMigrateCommand).to.not.have.members(mockCartItems)
      })
    })
  })
})
