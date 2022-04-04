import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { waitForIt } from '../../helper/sleep'
import { CartItem } from '../../../src/common/cart-item'
import { applicationUnderTest } from './setup'
import { beforeHookException, beforeHookProductId, throwExceptionId } from '../../../src/constants'

describe('Read models end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await applicationUnderTest.graphql.client()
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

      it('should apply modified filter by before hooks', async () => {
        // We create a cart with id 'before-fn-test-modified', but we query for
        // 'before-fn-test', which will then change the filter after two "before" functions
        // to return the original cart (id 'before-fn-test-modified')
        const variables = {
          cartId: 'before-fn-test-modified',
          productId: beforeHookProductId,
          quantity: 1,
        }
        await client.mutate({
          variables,
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
        })
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                cartId: 'before-fn-test',
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

        expect(cartData.id).to.be.equal(variables.cartId)
      })

      it('should return exceptions thrown by before functions', async () => {
        try {
          await waitForIt(
            () => {
              return client.query({
                variables: {
                  cartId: throwExceptionId,
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
            (_) => true
          )
        } catch (e) {
          expect(e.graphQLErrors[0].message).to.be.eq(beforeHookException)
          expect(e.graphQLErrors[0].path).to.deep.eq(['CartReadModel'])
        }
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

      // TODO this test is failing in local because of local provider doesn't provides optimistic concurrency control
      // TODO Remove condition when it will be fixed
      it('should retrieve expected cart', async () => {
        if (process.env.TESTED_PROVIDER === 'LOCAL') {
          return
        }
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

    context('query lists of carts', () => {
      let mockCartId: string
      const mockCartItems: Array<{ productId: string; quantity: number }> = []
      let mockProductId: string
      let mockQuantity: number
      let mockAddress: {
        firstName: string
        lastName: string
        country: string
        address: string
        postalCode: string
        state: string
      }

      beforeEach(async () => {
        mockCartId = random.uuid()

        mockProductId = random.uuid()
        mockQuantity = 2
        mockCartItems.push({ productId: mockProductId, quantity: mockQuantity })

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

        await waitForIt(
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
          (result) => result?.data?.CartReadModel.id !== undefined
        )

        mockAddress = {
          firstName: random.word(),
          lastName: random.word(),
          country: random.word(),
          state: random.word(),
          postalCode: random.word(),
          address: random.word(),
        }

        await client.mutate({
          variables: {
            cartId: mockCartId,
            address: mockAddress,
          },
          mutation: gql`
            mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput) {
              UpdateShippingAddress(input: { cartId: $cartId, address: $address })
            }
          `,
        })

        const filter = {
          shippingAddress: {
            firstName: { eq: mockAddress.firstName },
          },
        }
        await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: filter,
              },
              query: gql`
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length >= 1
        )
      })

      it('should retrieve a list of carts', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.query({
              query: gql`
                query CartReadModels {
                  CartReadModels {
                    id
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length >= 1
        )

        const cartData = queryResult.data.CartReadModels

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.be.gte(1)
      })

      it('should retrieve a specific cart using filters', async () => {
        const filter = { id: { eq: mockCartId } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: filter,
              },
              query: gql`
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length >= 1
        )

        const cartData = queryResult.data.CartReadModels

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })

      it('should retrieve a list of carts using nested filters', async () => {
        const filter = { shippingAddress: { firstName: { eq: mockAddress.firstName } } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: filter,
              },
              query: gql`
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length >= 1
        )

        const cartData = queryResult.data.CartReadModels

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })

      it('should retrieve a list of carts using  complex filters', async () => {
        const filter = { cartItems: { includes: { productId: mockProductId, quantity: 2 } } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: filter,
              },
              query: gql`
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModels?.length >= 1
        )

        const cartData = queryResult.data.CartReadModels

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })

      it('should retrieve a list of carts using paginated read model', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.query({
              query: gql`
                query ListCartReadModels {
                  ListCartReadModels {
                    items {
                      id
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items.length >= 1
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.be.gte(1)
      })

      it('should retrieve a specific cart using filters using paginated read model', async () => {
        const filter = { id: { eq: mockCartId } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: filter,
              },
              query: gql`
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items.length >= 1
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })

      it('should retrieve a list of carts using complex filters using paginated read model', async () => {
        const filter = { cartItems: { includes: { productId: mockProductId, quantity: 2 } } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: filter,
              },
              query: gql`
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items?.length >= 1
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })
    })

    context('query using pagination', () => {
      const mockCartIds: Array<string> = []
      const mockCartItems: Array<{ productId: string; quantity: number }> = []
      let mockProductId: string
      let mockQuantity: number
      const changeCartPromises: Array<Promise<unknown>> = []
      const cartsNumber = 3

      beforeEach(async () => {
        mockProductId = random.uuid()
        mockQuantity = 2
        mockCartItems.push({ productId: mockProductId, quantity: mockQuantity })

        for (let i = 0; i < cartsNumber; i++) {
          const mockCartId = random.uuid()
          mockCartIds.push(mockCartId)

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

      it('should retrieve a list of carts limited to 2 items', async () => {
        const limit = 2
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                limit: limit,
              },
              query: gql`
                query ListCartReadModels($limit: Int) {
                  ListCartReadModels(limit: $limit) {
                    items {
                      id
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items.length == 2
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(2)
      })

      it('should retrieve a list of carts paginated', async () => {
        const limit = 1
        let cursor: Record<'id', string> | undefined = undefined

        for (let i = 0; i < cartsNumber; i++) {
          const queryResult = await waitForIt(
            () => {
              return client.query({
                variables: {
                  limit: limit,
                  afterCursor: cursor,
                },
                query: gql`
                  query ListCartReadModels($limit: Int, $afterCursor: JSONObject) {
                    ListCartReadModels(limit: $limit, afterCursor: $afterCursor) {
                      cursor
                      items {
                        id
                      }
                    }
                  }
                `,
              })
            },
            (result) => result?.data?.ListCartReadModels?.items.length === 1
          )

          const currentPageCartData = queryResult.data.ListCartReadModels.items

          cursor = queryResult.data.ListCartReadModels.cursor

          if (cursor) {
            if (process.env.TESTED_PROVIDER === 'AZURE' || process.env.TESTED_PROVIDER === 'LOCAL') {
              expect(cursor.id).to.equal((i + 1).toString())
            } else {
              expect(cursor.id).to.equal(currentPageCartData[0].id)
            }
          }
          expect(currentPageCartData).to.be.an('array')
          expect(currentPageCartData.length).to.equal(1)
          expect(cursor).to.not.be.undefined
        }
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
                    confirmationToken
                    id
                    cartId
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
