import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { createUser, getUserAuthInformation, graphQLClient } from '../providers/aws/utils'
import { commerce, finance, internet, lorem, random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { waitForIt } from '../helper/sleep'
import { CartItem } from '../../src/common/cart-item'
import { createPassword } from '../helper/auth-helper'

describe('Read models end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>
  let loggedClient: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
    const userEmail = internet.email()
    const userPassword = createPassword()
    // TODO: Make retrieval of auth token cloud agnostic
    await createUser(userEmail, userPassword, 'UserWithEmail')
    const userAuthInformation = await getUserAuthInformation(userEmail, userPassword)
    loggedClient = await graphQLClient(userAuthInformation.idToken)
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

    context('using filters', () => {
      let mockCartId: string
      let mockCartItemsCount: number
      const mockCartItems: Array<{ productId: string; quantity: number }> = []
      let mockProductId: string
      let mockQuantity: number

      let mockSku: string
      let mockDisplayName: string
      let mockDescription: string
      let mockPriceInCents: number
      let mockCurrency: string

      beforeEach(async () => {
        mockCartId = `demo-${random.uuid()}`
        mockCartItemsCount = random.number({ min: 2, max: 5 })
        const changeCartPromises: Array<Promise<unknown>> = []

        for (let i = 0; i < mockCartItemsCount; i++) {
          mockProductId = random.uuid()
          mockQuantity = random.number({ min: 10 })
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

        mockSku = random.uuid()
        mockDisplayName = commerce.productName()
        mockDescription = lorem.paragraph()
        mockPriceInCents = random.number({ min: 1000, max: 4000 })
        mockCurrency = finance.currencyCode()

        // Create one product
        await loggedClient.mutate({
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
      })

      it('should retrieve an array of existing carts', async () => {
        const minimumExpectedCarts = 3
        const queryResult = await waitForIt(
          () => {
            return client.query({
              query: gql`
                query CartReadModels {
                  CartReadModels {
                    id
                    cartItems
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length > minimumExpectedCarts
        )

        const cartList: Array<any> = queryResult.data.CartReadModels
        expect(cartList).to.have.length.greaterThan(minimumExpectedCarts)
      })

      it('should retrieve all carts which ID begins with "demo-"', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                idStartsWith: 'demo-',
              },
              query: gql`
                query CartReadModels($idStartsWith: String) {
                  CartReadModels(filter: { id: { beginsWith: $idStartsWith } }) {
                    id
                    cartItems
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length > 0
        )

        const cartList: Array<any> = queryResult.data.CartReadModels
        const isWellFiltered = cartList.every((cart) => (cart.id as string).startsWith('demo-'))

        expect(isWellFiltered).to.be.true
      })

      it('should retrieve all products which Price is between 1000 and 4000', async () => {
        const minPrice = 1000
        const maxPrice = 4000
        const queryResult = await waitForIt(
          () => {
            return loggedClient.query({
              variables: {
                minPrice,
                maxPrice,
              },
              query: gql`
                query ProductReadModels($minPrice: Float, $maxPrice: Float) {
                  ProductReadModels(filter: { price: { cents: { gte: $minPrice, lte: $maxPrice } } }) {
                    id
                    price {
                      cents
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ProductReadModels.length > 0
        )

        const productList: Array<any> = queryResult.data.ProductReadModels
        const isWellFiltered = productList.every(
          (product) => product.price.cents >= minPrice && product.price.cents <= maxPrice
        )

        expect(isWellFiltered).to.be.true
      })

      it('should retrieve all carts with an specific product', async () => {
        const queryResult = await waitForIt(
          () => {
            return loggedClient.query({
              variables: {
                // Is not possible yet to filter a complex property inside an Array without using all its properties
                productId: mockProductId,
                quantity: mockQuantity,
              },
              query: gql`
                query CartReadModels($productId: ID!, $quantity: Float) {
                  CartReadModels(filter: { cartItems: { includes: { productId: $productId, quantity: $quantity } } }) {
                    id
                    cartItems
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels.length > 0
        )

        const cartList: Array<any> = queryResult.data.CartReadModels
        const isWellFiltered = cartList.every((cart) =>
          cart.cartItems.some(
            (cartItem: Record<string, any>) =>
              cartItem.productId === mockProductId && cartItem.quantity === mockQuantity
          )
        )

        expect(isWellFiltered).to.be.true
      })

      it('should filter by item ID from an Array of items IDs', async () => {
        const queryResult = await waitForIt(
          () => {
            return loggedClient.query({
              variables: {
                productId: mockProductId,
              },
              query: gql`
                query CartReadModels($productId: String) {
                  CartReadModels(filter: { cartItemsIds: { includes: $productId } }) {
                    id
                    cartItemsIds
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels.length > 0
        )

        const cartList: Array<any> = queryResult.data.CartReadModels
        const isWellFiltered = cartList.every((cart) =>
          cart.cartItemsIds.some((cartItemId: string) => cartItemId === mockProductId)
        )

        expect(isWellFiltered).to.be.true
      })

      context('filter complex properties', () => {
        let productId: string

        beforeEach(async () => {
          const productsBySku = await waitForIt(
            () => {
              return loggedClient.query({
                variables: {
                  sku: mockSku,
                },
                query: gql`
                  query ProductReadModels($sku: String) {
                    ProductReadModels(filter: { sku: { eq: $sku } }) {
                      id
                    }
                  }
                `,
              })
            },
            (result) => result?.data?.ProductReadModels.length > 0
          )
          productId = productsBySku.data.ProductReadModels[0]?.id
        })

        it('should filter by an interface property as JSONObject', async () => {
          const queryResult = await waitForIt(
            () => {
              return loggedClient.query({
                variables: {
                  productId,
                },
                query: gql`
                  query ProductsBySKUs($productId: String) {
                    ProductsBySKUs(filter: { firstProduct: { productId: { eq: $productId } } }) {
                      id
                      firstProduct
                    }
                  }
                `,
              })
            },
            (result) => result?.data?.ProductsBySKUs.length > 0
          )

          const productList: Array<any> = queryResult.data.ProductsBySKUs
          const isWellFiltered = productList.every(
            (productsBySkus) => productsBySkus.firstProduct.productId === productId
          )

          expect(isWellFiltered).to.be.true
        })

        it('should filter by a record property', async () => {
          const queryResult = await waitForIt(
            () => {
              return loggedClient.query({
                variables: {
                  items: 1,
                },
                query: gql`
                  query ProductsBySKUs($items: Float) {
                    ProductsBySKUs(filter: { record: { items: { gte: $items } } }) {
                      id
                      record
                    }
                  }
                `,
              })
            },
            (result) => result?.data?.ProductsBySKUs.length > 0
          )

          const productList: Array<any> = queryResult.data.ProductsBySKUs
          const isWellFiltered = productList.every((productsBySkus) => productsBySkus.record.items >= 1)

          expect(isWellFiltered).to.be.true
        })
      })
    })
  })

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
                  payment {
                    cartId
                    confirmationToken
                    id
                  }
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
          __typename: 'Payment',
          confirmationToken: mockConfirmationToken,
          id: mockPaymentId,
          cartId: mockCartId,
        },
      }

      expect(updatedCartData).to.be.deep.equal(expectedUpdatedResult)
    })
  })
})
