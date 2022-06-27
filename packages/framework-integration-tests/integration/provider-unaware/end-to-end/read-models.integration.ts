/* eslint-disable @typescript-eslint/ban-ts-comment */
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { commerce, finance, internet, lorem, random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { waitForIt } from '../../helper/sleep'
import { CartItem } from '../../../src/common/cart-item'
import { applicationUnderTest } from './setup'
import { beforeHookException, beforeHookProductId, throwExceptionId } from '../../../src/constants'
import { UUID } from '@boostercloud/framework-types'

describe('Read models end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = applicationUnderTest.graphql.client()
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
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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
                    cartItems {
                      productId
                      quantity
                    }
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
          __typename: 'CartItem',
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
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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
                    cartItems {
                      productId
                      quantity
                    }
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
                      cartItems {
                        productId
                        quantity
                      }
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
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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
                    cartItems {
                      productId
                      quantity
                    }
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
      const mockConfirmationToken: string = random.alphaNumeric(10)
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
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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
                    cartItems {
                      productId
                      quantity
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModel?.id === mockCartId
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
            mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
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
                    shippingAddress {
                      firstName
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const carts = result?.data?.CartReadModels
            return carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName
          }
        )
      })

      it('should retrieve a list of carts using deprecated methods', async () => {
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

      it('should retrieve a specific cart using filters using deprecated methods', async () => {
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

      it('should retrieve a list of carts using  complex filters and deprecated methods', async () => {
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

      it('should retrieve a list of carts when filter by isDefined true', async () => {
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              shippingAddress: {
                firstName: {
                  isDefined: true,
                },
              },
            },
          ],
        }
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
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const carts = result?.data?.ListCartReadModels?.items
            return carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName
          }
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
      })

      it('should retrieve a list of carts when filter by isDefined false', async () => {
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              payment: {
                id: {
                  isDefined: false,
                },
              },
            },
          ],
        }
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
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const carts = result?.data?.ListCartReadModels?.items
            return carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName
          }
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
      })

      it('should retrieve a list of carts when filter by isDefined true with AND', async () => {
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              and: [
                {
                  shippingAddress: {
                    firstName: {
                      isDefined: true,
                    },
                  },
                },
                {
                  shippingAddress: {
                    firstName: { eq: mockAddress.firstName },
                  },
                },
              ],
            },
          ],
        }
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
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const carts = result?.data?.ListCartReadModels?.items
            return carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName
          }
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
      })

      it('should retrieve a list of carts when filter by isDefined true with OR', async () => {
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              or: [
                {
                  shippingAddress: {
                    lastName: {
                      isDefined: false,
                    },
                  },
                },
                {
                  shippingAddress: {
                    firstName: { eq: mockAddress.firstName },
                  },
                },
              ],
            },
          ],
        }
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
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const carts = result?.data?.ListCartReadModels?.items
            return carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName
          }
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
      })

      it('should retrieve a list of carts when filter by isDefined for Objects', async () => {
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              payment: {
                isDefined: false,
              },
            },
          ],
        }

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
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const carts = result?.data?.ListCartReadModels?.items
            return carts?.length >= 1 && carts[0].shippingAddress?.firstName === mockAddress.firstName
          }
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
      })

      it('should retrieve a list of carts when filter by isDefined with complex queries', async () => {
        const mockPaymentId: string = random.uuid()
        await client.mutate({
          variables: {
            paymentId: mockPaymentId,
            cartId: mockCartId,
            confirmationToken: mockConfirmationToken,
          },
          mutation: gql`
            mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
              ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
            }
          `,
        })

        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              shippingAddress: {
                firstName: {
                  eq: mockAddress.firstName,
                },
              },
            },
            {
              or: [
                {
                  cartItems: {
                    isDefined: false,
                  },
                },
                {
                  cartItems: {
                    includes: { productId: mockProductId, quantity: 2 },
                  },
                },
              ],
            },
            {
              payment: {
                confirmationToken: { eq: mockConfirmationToken },
              },
            },
            {
              payment: {
                id: { ne: null },
              },
            },
          ],
        }

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
                      cartItems {
                        productId
                        quantity
                      }
                      shippingAddress {
                        firstName
                      }
                      payment {
                        id
                        confirmationToken
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) =>
            result?.data?.ListCartReadModels?.items.length >= 1 &&
            result?.data?.ListCartReadModels?.items[0]?.payment?.id !== undefined
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
        expect(cartData[0].cartItems[0].productId).to.be.eq(mockProductId)
        expect(cartData[0].payment.id).to.be.eq(mockPaymentId)
      })

      it('should retrieve a list of carts when filter by null', async () => {
        const mockPaymentId: string = random.uuid()
        await client.mutate({
          variables: {
            paymentId: mockPaymentId,
            cartId: mockCartId,
            confirmationToken: mockConfirmationToken,
          },
          mutation: gql`
            mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
              ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
            }
          `,
        })

        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              payment: {
                confirmationToken: { ne: null },
              },
            },
            {
              payment: {
                id: { ne: null },
              },
            },
          ],
        }

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
                      cartItems {
                        productId
                        quantity
                      }
                      shippingAddress {
                        firstName
                      }
                      payment {
                        id
                        confirmationToken
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) =>
            result?.data?.ListCartReadModels?.items.length >= 1 &&
            result?.data?.ListCartReadModels?.items[0]?.payment?.id !== undefined
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
        expect(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName)
        expect(cartData[0].cartItems[0].productId).to.be.eq(mockProductId)
        expect(cartData[0].payment.id).to.be.eq(mockPaymentId)
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

      it('should retrieve a list of carts using AND by default with paginated read model', async () => {
        const filter = {
          shippingAddress: {
            firstName: {
              eq: mockAddress.firstName,
            },
          },
          cartItems: {
            includes: {
              productId: mockProductId,
              quantity: mockQuantity,
            },
          },
          cartItemsIds: {
            includes: mockProductId,
          },
        }
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
                      cartItems {
                        productId
                        quantity
                      }
                      checks
                      shippingAddress {
                        firstName
                      }
                      payment {
                        cartId
                      }
                      cartItemsIds
                    }
                    cursor
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items?.length === 1
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })

      it('should not fail if search a list of carts with empty results', async () => {
        const filter = { cartItems: { includes: { productId: mockProductId, quantity: 200 } } }
        const queryResult = await client.query({
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

        const cartData = queryResult?.data?.ListCartReadModels.items

        expect(cartData.length).to.equal(0)
      })
    })

    context('query sorted lists of carts', () => {
      const mockCartItems: Array<{ id: string; productId: string; quantity: number; firstName: string }> = []
      const cartItems = 5
      let mockAddress: {
        firstName: string
        lastName: string
        country: string
        address: string
        postalCode: string
        state: string
      }

      beforeEach(async () => {
        for (let i = 0; i < cartItems; i++) {
          const mockQuantity: number = i
          const mockProductId: string = random.uuid()
          const mockCartId: string = random.uuid()
          const mockFirstName = String.fromCharCode(i + 65)
          mockAddress = {
            firstName: mockFirstName,
            lastName: random.word(),
            country: random.word(),
            state: random.word(),
            postalCode: random.word(),
            address: random.word(),
          }
          mockCartItems.push({
            id: mockCartId,
            productId: mockProductId,
            quantity: mockQuantity,
            firstName: mockFirstName,
          })

          await client.mutate({
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
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                `,
              })
            },
            (result) => result?.data?.CartReadModel != null
          )

          await client.mutate({
            variables: {
              cartId: mockCartId,
              address: mockAddress,
            },
            mutation: gql`
              mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
                UpdateShippingAddress(input: { cartId: $cartId, address: $address })
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
                      cartItems {
                        productId
                        quantity
                      }
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                `,
              })
            },
            (result) => result?.data?.CartReadModel?.shippingAddress?.firstName != null
          )
        }
      })

      afterEach(async () => {
        mockCartItems.length = 0
      })

      it('should retrieve a sorted list of carts using paginated read model', async () => {
        if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
          console.log('****************** Warning **********************')
          console.log('Only Azure and Local provider implement the sort option')
          console.log('*************************************************')
          return
        }

        const expectedIds = mockCartItems.map((item) => item.id)
        const mockedSortBy = { id: 'DESC' }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filterBy: { id: { in: expectedIds } },
                sortBy: mockedSortBy,
              },
              query: gql`
                query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
                  ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
                    items {
                      id
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items.length === cartItems
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        const reverseExpectedIds = expectedIds.sort((a, b) => {
          return a > b ? -1 : a < b ? 1 : 0
        })
        // @ts-ignore
        expect(cartData.map((item: unknown) => item.id)).to.be.eql(reverseExpectedIds)
      })

      it('should retrieve a sorted list of carts using nested fields', async () => {
        if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
          console.log('****************** Warning **********************')
          console.log('Only Azure and Local provider implement the sort option')
          console.log('*************************************************')
          return
        }

        const expectedIds = mockCartItems.map((item) => item.id)
        const mockedSortBy = { shippingAddress: { firstName: 'DESC' } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filterBy: { id: { in: expectedIds } },
                sortBy: mockedSortBy,
              },
              query: gql`
                query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
                  ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items.length === cartItems
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        const expectedFirstNames = mockCartItems.map((item) => item.firstName)
        const reverseExpectedFirstNames = expectedFirstNames.sort((a, b) => {
          return a > b ? -1 : a < b ? 1 : 0
        })
        // @ts-ignore
        const names = cartData.map((item: unknown) => item.shippingAddress.firstName)
        expect(names).to.be.eql(reverseExpectedFirstNames)
      })

      it('should retrieve a sorted list of carts using two fields', async () => {
        if (process.env.TESTED_PROVIDER !== 'LOCAL') {
          console.log('****************** Warning **********************')
          console.log('Only Local provider implement the sort option for more than one sort field')
          console.log('*************************************************')
          return
        }

        const expectedIds = mockCartItems.map((item) => item.id)
        const mockedSortBy = { id: 'DESC', shippingAddress: { firstName: 'ASC' } }
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filterBy: { id: { in: expectedIds } },
                sortBy: mockedSortBy,
              },
              query: gql`
                query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
                  ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
                    items {
                      id
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCartReadModels?.items.length === cartItems
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        const reverseExpectedIds = expectedIds.sort((a, b) => {
          return a > b ? -1 : a < b ? 1 : 0
        })
        // @ts-ignore
        expect(cartData.map((item: unknown) => item.id)).to.be.eql(reverseExpectedIds)
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
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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
                  cartItems {
                    productId
                    quantity
                  }
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
            __typename: 'CartItem',
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
          mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
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
                  cartItems {
                    productId
                    quantity
                  }
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
            __typename: 'CartItem',
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

  describe('projecting two entities with array joinKey', () => {
    let client: ApolloClient<NormalizedCacheObject>
    let userToken: string

    before(async () => {
      const userEmail = internet.email()
      userToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail')
      client = applicationUnderTest.graphql.client(userToken)
    })

    const oneMockProductId: string = random.uuid()
    const twoMockProductId: string = random.uuid()

    beforeEach(async () => {
      // Add item one
      await client.mutate({
        variables: {
          productID: oneMockProductId,
          sku: random.uuid(),
          displayName: commerce.productName(),
          description: lorem.paragraph(),
          priceInCents: random.number({ min: 1 }),
          currency: finance.currencyCode(),
        },
        mutation: gql`
          mutation CreateProduct(
            $productID: ID!
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
          ) {
            CreateProduct(
              input: {
                productID: $productID
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

      // Add item two
      await client.mutate({
        variables: {
          productID: twoMockProductId,
          sku: random.uuid(),
          displayName: commerce.productName(),
          description: lorem.paragraph(),
          priceInCents: random.number({ min: 1 }),
          currency: finance.currencyCode(),
        },
        mutation: gql`
          mutation CreateProduct(
            $productID: ID!
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
          ) {
            CreateProduct(
              input: {
                productID: $productID
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

    it('should project changes for both entities', async () => {
      // Check that new product is available in read model
      const products = await waitForIt(
        () => {
          return client.query({
            variables: {
              products: [oneMockProductId, twoMockProductId],
            },
            query: gql`
              query ProductReadModels($products: [ID!]!) {
                ProductReadModels(filter: { id: { in: $products } }) {
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
                  packs {
                    id
                  }
                }
              }
            `,
          })
        },
        (result) =>
          result?.data?.ProductReadModels?.length == 2 &&
          result?.data?.ProductReadModels?.every(
            (product: any) => Array.isArray(product.packs) && product.packs.length == 0
          )
      )

      expect(products.data.ProductReadModels.length).to.be.equal(2)

      const mockPackId: string = random.uuid()
      const mockPackName: string = commerce.productName()
      const mockPackProducts: Array<UUID> = [oneMockProductId, twoMockProductId]

      // Create Pack
      await client.mutate({
        variables: {
          packID: mockPackId,
          name: mockPackName,
          products: mockPackProducts,
        },
        mutation: gql`
          mutation CreatePack($packID: ID!, $name: String!, $products: [ID!]!) {
            CreatePack(input: { packID: $packID, name: $name, products: $products })
          }
        `,
      })

      const updatedQueryResults = await waitForIt(
        () => {
          return client.query({
            variables: {
              products: [oneMockProductId, twoMockProductId],
            },
            query: gql`
              query ProductReadModels($products: [ID!]!) {
                ProductReadModels(filter: { id: { in: $products } }) {
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
                  packs {
                    id
                    name
                    products
                  }
                }
              }
            `,
          })
        },
        (result) =>
          result?.data?.ProductReadModels?.length == 2 &&
          result?.data?.ProductReadModels?.every(
            (product: any) => Array.isArray(product.packs) && product.packs.length == 1
          )
      )

      const updatedProducts = updatedQueryResults.data.ProductReadModels
      expect(updatedProducts.length).to.be.equal(2)

      updatedProducts.forEach((product: { packs: Array<any> }) => {
        expect(product.packs).to.be.deep.equal([
          {
            __typename: 'Pack',
            id: mockPackId,
            name: mockPackName,
            products: mockPackProducts,
          },
        ])
      })
    })
  })

  describe('read model authorization', () => {
    context('with an anonymous user', () => {
      let anonymousClient: ApolloClient<NormalizedCacheObject>

      beforeEach(() => {
        anonymousClient = applicationUnderTest.graphql.client()
      })

      context('with a public read model', () => {
        it('should be accessible', async () => {
          const resultPromise = anonymousClient.query({
            variables: {
              cartId: 'mockCartId',
            },
            query: gql`
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.be.eventually.fulfilled
        })
      })

      context('with a read model authorized for certain roles', () => {
        it('should not be accessible', async () => {
          const resultPromise = anonymousClient.query({
            variables: {
              id: 'mockId',
            },
            query: gql`
              query ProductReadModel($id: ID!) {
                ProductReadModel(id: $id) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.rejectedWith(/Access denied for this resource/)
        })
      })

      context('with a read model with a custom authorizer', () => {
        it('should not be accessible', async () => {
          const resultPromise = anonymousClient.query({
            variables: {
              id: 'mockId',
            },
            query: gql`
              query SpecialReportsReadModel($id: ID!) {
                SpecialReportsReadModel(id: $id) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.rejectedWith(/You are not allowed to see such insightful report/)
        })
      })
    })

    context('with a user with a role', () => {
      let loggedClient: ApolloClient<NormalizedCacheObject>

      beforeEach(() => {
        const userToken = applicationUnderTest.token.forUser(internet.email(), 'UserWithEmail')
        loggedClient = applicationUnderTest.graphql.client(userToken)
      })

      context('with a public read model', () => {
        it('should be accessible', async () => {
          const resultPromise = loggedClient.query({
            variables: {
              cartId: 'mockCartId',
            },
            query: gql`
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.fulfilled
        })
      })

      context('with a read model authorized for matching roles', () => {
        it('should be accessible', async () => {
          const resultPromise = loggedClient.query({
            variables: {
              id: 'mockId',
            },
            query: gql`
              query ProductReadModel($id: ID!) {
                ProductReadModel(id: $id) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.fulfilled
        })
      })

      context('with a read model with a custom authorizer', () => {
        it('should not be accessible', async () => {
          const resultPromise = loggedClient.query({
            variables: {
              id: 'mockId',
            },
            query: gql`
              query SpecialReportsReadModel($id: ID!) {
                SpecialReportsReadModel(id: $id) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.rejectedWith(/You are not allowed to see such insightful report/)
        })
      })
    })

    context('with a user that fulfills the custom authorizer', () => {
      let knowledgeableClient: ApolloClient<NormalizedCacheObject>

      beforeEach(() => {
        const tokenWithSpecialAccess = applicationUnderTest.token.forUser(internet.email(), undefined, {
          customClaims: {
            specialReportAccess: 'true',
          },
        })
        knowledgeableClient = applicationUnderTest.graphql.client(tokenWithSpecialAccess)
      })

      context('with a public read model', () => {
        it('should be accessible', async () => {
          const resultPromise = knowledgeableClient.query({
            variables: {
              cartId: 'mockCartId',
            },
            query: gql`
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.fulfilled
        })
      })

      context('with a read model authorized for certain roles', () => {
        it('should not be accessible', async () => {
          const resultPromise = knowledgeableClient.query({
            variables: {
              id: 'mockId',
            },
            query: gql`
              query ProductReadModel($id: ID!) {
                ProductReadModel(id: $id) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.rejectedWith(/Access denied for this resource/)
        })
      })

      context('with a read model with a custom authorizer', () => {
        it('should be accessible', async () => {
          const resultPromise = knowledgeableClient.query({
            variables: {
              id: 'mockId',
            },
            query: gql`
              query SpecialReportsReadModel($id: ID!) {
                SpecialReportsReadModel(id: $id) {
                  id
                }
              }
            `,
          })

          await expect(resultPromise).to.eventually.be.fulfilled
        })
      })
    })
  })
})
