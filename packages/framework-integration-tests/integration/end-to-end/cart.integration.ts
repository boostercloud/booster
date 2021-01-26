import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { getTokenForUser, graphQLClient, waitForIt } from '../providers/aws/utils'
import { random, commerce, finance, lorem, internet } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { CartItem } from '../../src/common/cart-item'
import { sleep } from '../helper/sleep'

describe('Cart end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
  })

  describe('Commands', () => {
    it('accepts a command successfully', async () => {
      const response = await client.mutate({
        variables: {
          cartId: random.uuid(),
          productId: random.uuid(),
          quantity: random.number({ min: 1 }),
        },
        mutation: gql`
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
      })

      expect(response).not.to.be.null
      expect(response?.data?.ChangeCartItem).to.be.true
    })

    describe('Query read models', () => {
      context('1 cart item', () => {
        const mockCartId: string = random.uuid()
        const mockProductId: string = random.uuid()
        const mockQuantity: number = random.number({ min: 1 })

        beforeEach(async () => {
          // provisioning a cart
          await client.mutate({
            variables: {
              cartId: mockCartId,
              productId: mockProductId,
              quantity: mockQuantity,
            },
            mutation: gql`
              mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
                ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
              }
            `,
          })
        })

        it('should retrieve expected cart', async () => {
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

          const cartData = queryResult.data.CartReadModel

          expect(cartData.id).to.be.equal(mockCartId)
          expect(cartData.cartItems).to.have.length(1)
          expect(cartData.cartItems[0]).to.deep.equal({
            productId: mockProductId,
            quantity: mockQuantity,
          })
        })
      })

      context('several cart items', () => {
        let mockCartId: string
        let mockCartItemsCount: number
        const mockCartItems: Array<{ productId: string; quantity: number }> = []

        beforeEach(async () => {
          mockCartId = random.uuid()
          mockCartItemsCount = random.number({ min: 2, max: 5 })
          const changeCartPromises: Array<Promise<unknown>> = []

          for (let i = 0; i < mockCartItemsCount; i++) {
            const mockProductId: string = random.uuid()
            const mockQuantity: number = random.number({ min: 1 })
            mockCartItems.push({ productId: mockProductId, quantity: mockQuantity })

            changeCartPromises.push(
              client.mutate({
                variables: {
                  cartId: mockCartId,
                  productId: mockProductId,
                  quantity: mockQuantity,
                },
                mutation: gql`
                  mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
                    ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
                  }
                `,
              })
            )
          }

          await Promise.all(changeCartPromises)
        })

        it('should retrieve expected cart', async () => {
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
            (result) => result?.data?.CartReadModel?.cartItems?.length == mockCartItemsCount
          )

          const cartData = queryResult.data.CartReadModel

          expect(cartData.id).to.be.equal(mockCartId)
          expect(cartData.cartItems).to.have.length(mockCartItemsCount)

          mockCartItems.forEach((mockCartItem: CartItem) => {
            const hasCartItem = cartData.cartItems.some(
              (cartItem: CartItem) =>
                cartItem.productId === mockCartItem.productId && cartItem.quantity === mockCartItem.quantity
            )

            expect(hasCartItem).to.be.true
          })
        })
      })
    })
  })

  describe('Entities', () => {
    let userEmail: string
    let authToken: string

    before(async () => {
      userEmail = internet.email()
      authToken = await getTokenForUser(userEmail, 'UserWithEmail')
      client = await graphQLClient(authToken)
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

        const product = products.data.ProductReadModels.find((product: any) => product.sku === mockSku)
        productId = product.id

        const expectedResult = {
          __typename: 'ProductReadModel',
          id: productId,
          sku: mockSku,
          displayName: mockDisplayName,
          description: mockDescription,
          price: {
            cents: mockPriceInCents,
            currency: mockCurrency,
          },
          availability: 0,
          deleted: false,
        }

        expect(product).to.be.deep.equal(expectedResult)
      })

      it('should reduce the entity as expected', async () => {
        const adminEmail: string = internet.email()
        const adminAuthToken = await getTokenForUser(adminEmail, 'Admin')
        client = await graphQLClient(adminAuthToken)

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

        client = await graphQLClient(authToken)
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
                    price
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

  describe('Read models', () => {
    describe('projecting two entities', () => {
      const mockCartId: string = random.uuid()
      const mockPaymentId: string = random.uuid()
      const mockProductId: string = random.uuid()
      const mockQuantity: number = random.number({ min: 1 })
      const mockConfirmationToken: string = random.alphaNumeric(10)

      beforeEach(async () => {
        // provisioning a cart
        await client.mutate({
          variables: {
            cartId: mockCartId,
            productId: mockProductId,
            quantity: mockQuantity,
          },
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
        })
      })

      it('should project changes for both entities', async () => {
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

        const cartData = queryResult.data.CartReadModel
        const expectedResult = {
          __typename: 'CartReadModel',
          id: mockCartId,
          cartItems: [
            {
              productId: mockProductId,
              quantity: mockQuantity,
            },
          ],
        }

        expect(cartData).to.be.deep.equal(expectedResult)

        // Make payment
        await client.mutate({
          variables: {
            paymentId: mockPaymentId,
            cartId: mockCartId,
            confirmationToken: mockConfirmationToken,
          },
          mutation: gql`
            mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String) {
              ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
            }
          `,
        })

        // Retrieve updated read model
        const updatedQueryResult = await waitForIt(
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
                    payment
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModel?.payment != null
        )

        const updatedCartData = updatedQueryResult.data.CartReadModel
        const expectedUpdatedResult = {
          __typename: 'CartReadModel',
          id: mockCartId,
          cartItems: [
            {
              productId: mockProductId,
              quantity: mockQuantity,
            },
          ],
          payment: {
            confirmationToken: mockConfirmationToken,
            id: mockPaymentId,
            cartId: mockCartId,
          },
        }

        expect(updatedCartData).to.be.deep.equal(expectedUpdatedResult)
      })
    })
  })
})
