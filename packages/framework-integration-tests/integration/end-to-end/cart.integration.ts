import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient, waitForIt } from '../providers/aws/utils'
import { random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { CartItem } from '../../src/common/cart-item'

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

    describe('Read models', () => {
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
          mockCartItemsCount = random.number({ min: 2, max: 10 })

          for (let i = 0; i < mockCartItemsCount; i++) {
            const mockProductId: string = random.uuid()
            const mockQuantity: number = random.number({ min: 1 })
            mockCartItems.push({ productId: mockProductId, quantity: mockQuantity })

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
          }
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
            (result) => result?.data?.CartReadModel?.cartItems.length == mockCartItemsCount
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
})
