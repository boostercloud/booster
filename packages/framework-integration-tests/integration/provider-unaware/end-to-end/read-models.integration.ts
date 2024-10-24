/* eslint-disable @typescript-eslint/ban-ts-comment */
import { ApolloClient, ApolloQueryResult, gql, NormalizedCacheObject } from '@apollo/client'
import { commerce, finance, internet, lorem, random } from 'faker'
import { expect } from '../../helper/expect'
import { waitForIt } from '../../helper/sleep'
import { CartItem } from '../../../src/common/cart-item'
import { applicationUnderTest } from './setup'
import { beforeHookException, beforeHookProductId, throwExceptionId } from '../../../src/constants'
import { UUID } from '@boostercloud/framework-types'

let client: ApolloClient<NormalizedCacheObject>

describe('Read models end-to-end tests', () => {
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
      const mockCartItems: Array<{
        productId: string
        quantity: number
      }> = []

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
      const mockCartItems: Array<{
        productId: string
        quantity: number
      }> = []
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
        const filter = {
          and: [{ id: { eq: mockCartId } }, { shippingAddress: { firstName: { eq: mockAddress.firstName } } }],
        }
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
          (result) => result?.data?.CartReadModels?.length >= 1 && result?.data?.CartReadModels[0].id === mockCartId
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

      it('should retrieve a list of carts when filter by contains with UUID fields', async () => {
        const partialMockCartId = mockCartId.slice(1, -1)
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              id: {
                contains: partialMockCartId,
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
            return carts?.length >= 1 && carts[0].id === mockCartId
          }
        )

        const cartData = queryResult.data.ListCartReadModels.items

        expect(cartData).to.be.an('array')
        expect(cartData.length).to.equal(1)
        expect(cartData[0].id).to.equal(mockCartId)
      })

      it('should retrieve a list of carts when filter by regex', async () => {
        if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
          console.log('****************** Warning **********************')
          console.log('Only Azure and Local provider implement the regex filter. Skipping')
          console.log('*************************************************')
          return
        }
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              shippingAddress: {
                firstName: {
                  regex: `^${mockAddress.firstName.at(0)}.*`,
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

      it('should retrieve a list of carts when filter by iRegex', async () => {
        if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
          console.log('****************** Warning **********************')
          console.log('Only Azure and Local provider implement the iRegex filter. Skipping')
          console.log('*************************************************')
          return
        }
        const firstLetter = mockAddress.firstName.at(0)
        const inverseFirstLetter =
          firstLetter === firstLetter?.toUpperCase() ? firstLetter?.toLowerCase() : firstLetter?.toUpperCase()
        const filter = {
          and: [
            {
              id: { eq: mockCartId },
            },
            {
              shippingAddress: {
                firstName: {
                  iRegex: `^${inverseFirstLetter}.*`,
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
            result?.data?.ListCartReadModels?.items[0]?.id === mockCartId &&
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
      const mockCartItems: Array<{
        id: string
        productId: string
        quantity: number
        firstName: string
      }> = []
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
      const mockCartItems: Array<{
        productId: string
        quantity: number
      }> = []
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
                  query ListCartReadModels($limit: Int, $afterCursor: JSON) {
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

    context('projecting fields', () => {
      if (process.env.TESTED_PROVIDER === 'AWS') {
        console.log('AWS Provider ReadModel projecting field is not supported')
        return
      }

      const mockCartId: string = random.uuid()
      const mockProductId: string = random.uuid()
      const mockQuantity: number = random.number({ min: 1 })
      const mockAddress = {
        firstName: random.word(),
        lastName: random.word(),
        country: random.word(),
        state: random.word(),
        postalCode: random.word(),
        address: random.word(),
      }

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
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModel != null
        )

        // update shipping address
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
                    shippingAddress {
                      firstName
                    }
                  }
                }
              `,
            })
          },
          (result) =>
            result?.data?.CartReadModel != null &&
            result?.data?.CartReadModel?.shippingAddress?.firstName === mockAddress.firstName
        )
      })

      it('with paginatedVersion true', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.mutate({
              variables: {
                cartId: mockCartId,
                paginatedVersion: true,
              },
              mutation: gql`
                mutation CartShippingAddress($cartId: ID!, $paginatedVersion: Boolean!) {
                  CartShippingAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
                }
              `,
            })
          },
          (result) => result?.data?.CartShippingAddress != null
        )

        const cartShippingAddress = queryResult.data.CartShippingAddress

        expect(cartShippingAddress).to.deep.equal({
          items: [
            {
              id: mockCartId,
              shippingAddress: {
                firstName: mockAddress.firstName,
                lastName: mockAddress.lastName,
                country: mockAddress.country,
                state: mockAddress.state,
                postalCode: mockAddress.postalCode,
                address: mockAddress.address,
              },
            },
          ],
          count: 1,
          cursor: {
            id: '1',
          },
        })
      })

      it('with paginatedVersion false', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.mutate({
              variables: {
                cartId: mockCartId,
                paginatedVersion: false,
              },
              mutation: gql`
                mutation CartShippingAddress($cartId: ID!, $paginatedVersion: Boolean!) {
                  CartShippingAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
                }
              `,
            })
          },
          (result) => result?.data?.CartShippingAddress != null
        )

        const cartShippingAddress = queryResult.data.CartShippingAddress

        expect(cartShippingAddress).to.deep.equal([
          {
            id: mockCartId,
            shippingAddress: {
              firstName: mockAddress.firstName,
              lastName: mockAddress.lastName,
              country: mockAddress.country,
              state: mockAddress.state,
              postalCode: mockAddress.postalCode,
              address: mockAddress.address,
            },
          },
        ])
      })
    })

    context('query with fragments', () => {
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
        const fragment = gql`
          fragment cartItemDetails on CartItem {
            productId
            quantity
          }
        `
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
                      ...cartItemDetails
                    }
                  }
                }
                ${fragment}
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

      it('should retrieve list of items', async () => {
        const fragment = gql`
          fragment cart on CartReadModel {
            id
            cartItems {
              productId
              quantity
            }
          }
        `
        const limit = 1
        let cursor: Record<'id', string> | undefined = undefined

        for (let i = 0; i < limit; i++) {
          const queryResult = await waitForIt(
            () => {
              return client.query({
                variables: {
                  filterBy: { id: { eq: mockCartId } },
                },
                query: gql`
                  query ListCartReadModels($filterBy: ListCartReadModelFilter) {
                    ListCartReadModels(filter: $filterBy) {
                      items {
                        ...cart
                      }
                      cursor
                    }
                  }
                  ${fragment}
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
          expect(currentPageCartData[0].id).to.be.equal(mockCartId)
          expect(currentPageCartData[0].cartItems).to.have.length(1)
          expect(currentPageCartData[0].cartItems[0].productId).to.equal(mockProductId)
        }
      })
    })

    context('projecting calculated fields', () => {
      const mockCartId: string = random.uuid()
      const mockProductId: string = random.uuid()
      const mockQuantity: number = random.number({ min: 1 })
      const mockAddress = {
        firstName: random.word(),
        lastName: random.word(),
        country: random.word(),
        state: random.word(),
        postalCode: random.word(),
        address: random.word(),
      }

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
                  }
                }
              `,
            })
          },
          (result) => result?.data?.CartReadModel != null
        )

        // update shipping address
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
                    shippingAddress {
                      firstName
                    }
                  }
                }
              `,
            })
          },
          (result) =>
            result?.data?.CartReadModel != null &&
            result?.data?.CartReadModel?.shippingAddress?.firstName === mockAddress.firstName
        )
      })

      it('should correctly fetch calculated fields via GraphQL query', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.query({
              variables: {
                filter: {
                  id: { eq: mockCartId },
                },
              },
              query: gql`
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                    myAddress {
                      firstName
                      lastName
                      country
                      state
                      postalCode
                      address
                    }
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
        expect(cartData[0].id).to.be.equal(mockCartId)
        expect(cartData[0].myAddress).to.deep.equal({
          ...mockAddress,
          __typename: 'Address',
        })
      })

      it('should correctly fetch calculated fields via code', async () => {
        const queryResult = await waitForIt(
          () => {
            return client.mutate({
              variables: {
                cartId: mockCartId,
                paginatedVersion: true,
              },
              mutation: gql`
                mutation CartMyAddress($cartId: ID!, $paginatedVersion: Boolean!) {
                  CartMyAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
                }
              `,
            })
          },
          (result) => result?.data?.CartMyAddress != null
        )

        const cartMyAddress = queryResult.data.CartMyAddress

        expect(cartMyAddress).to.deep.equal({
          items: [
            {
              id: mockCartId,
              myAddress: {
                firstName: mockAddress.firstName,
                lastName: mockAddress.lastName,
                country: mockAddress.country,
                state: mockAddress.state,
                postalCode: mockAddress.postalCode,
                address: mockAddress.address,
              },
            },
          ],
          count: 1,
          cursor: {
            id: '1',
          },
        })
      })
    })
  })

  describe('projections', () => {
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
                    productDetails
                    productType
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
                    productDetails
                    productType
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

    it('should project with "entity joinkey" and create a new read model', async () => {
      await addModel()
    })

    it('should project with "entity joinkey" and update an existing read model', async () => {
      const fiatId = await addModel()
      await updateModel(fiatId)

      const johnId = await addOwner()
      await updateOwner(johnId)
    })

    it('should project with array "entity joinkey" and create a new read model', async () => {
      const fiatId = await addModel()
      const johnId = await addOwner()
      const johnFiatId = await buy(fiatId, johnId)
      const offer1Id = await addOffer([johnFiatId])
      const offer = await getCarOfferById(offer1Id)
      const johnFiat = await getPurchaseById(johnFiatId)
      expect(johnFiat.data.CarPurchasesReadModel.offers.length).to.be.eql(1)
      expect(johnFiat.data.CarPurchasesReadModel.offers[0].id).to.be.eql(offer.data.CarOfferReadModel.id)
    })

    it('should project with array "entity joinkey" and update an existing read model', async () => {
      const fiatId = await addModel()
      const johnId = await addOwner()
      const lucasId = await addOwner()
      const johnFiatId = await buy(fiatId, johnId)
      const lucasFiatId = await buy(fiatId, lucasId)
      const offer1Id = await addOffer([johnFiatId])
      const offer1 = await getCarOfferById(offer1Id)
      const offer2Id = await addOffer([johnFiatId, lucasFiatId])
      const offer2 = await getCarOfferById(offer2Id)
      const johnFiat = await getPurchaseById(johnFiatId)
      const lucasFiat = await getPurchaseById(lucasFiatId)
      expect(johnFiat.data.CarPurchasesReadModel.offers.length).to.be.eql(2)
      expect(johnFiat.data.CarPurchasesReadModel.offers[0].id).to.be.eql(offer1.data.CarOfferReadModel.id)
      expect(johnFiat.data.CarPurchasesReadModel.offers[1].id).to.be.eql(offer2.data.CarOfferReadModel.id)
      expect(lucasFiat.data.CarPurchasesReadModel.offers.length).to.be.eql(1)
      expect(johnFiat.data.CarPurchasesReadModel.offers[0].id).to.be.eql(offer1.data.CarOfferReadModel.id)
    })

    it('should project with "read model joinkey" and create a new read model', async () => {
      const fiatId = await addModel()
      const johnId = await addOwner()
      const johnFiatId = await buy(fiatId, johnId)
      const allPurchaseIds = [johnFiatId]
      const allPurchases = await purchasesByIds(allPurchaseIds)
      expect(allPurchases).to.be.lengthOf(1)
    })

    it('should project with "read model joinkey" and update an existing read model', async () => {
      const fiatId = await addModel()
      const johnId = await addOwner()
      const lucasId = await addOwner()
      const aliceId = await addOwner()
      const johnFiatId = await buy(fiatId, johnId)
      const lucasFiatId = await buy(fiatId, lucasId)
      const aliceFiatId = await buy(fiatId, aliceId)
      const allPurchaseIds = [johnFiatId, lucasFiatId, aliceFiatId]
      const allPurchases = await purchasesByIds(allPurchaseIds)
      expect(allPurchases).to.be.lengthOf(allPurchaseIds.length)

      await updateModel(fiatId)
      await updateOwner(johnId)
      const updatedPurchases = await purchasesByIds(allPurchaseIds)

      // All MODELS name should be updated
      expectModelNamesUpdated(allPurchases, johnFiatId, updatedPurchases)
      expectModelNamesUpdated(allPurchases, lucasFiatId, updatedPurchases)
      expectModelNamesUpdated(allPurchases, aliceFiatId, updatedPurchases)
      // Only John OWNER name should be updated
      expectFirstOwnerNamesUpdated(allPurchases, johnFiatId, updatedPurchases)
      expectFirstOwnerNamesNotUpdated(allPurchases, lucasFiatId, updatedPurchases)
      expectFirstOwnerNamesNotUpdated(allPurchases, aliceFiatId, updatedPurchases)
    })

    it('should NOT project when "read model joinkey" is undefined', async () => {
      const fiatId = await addModel()
      const johnId = await addOwner()
      const originalJohnOwner = await waitForId(johnId.toString(), 'CarOwnerReadModel', undefined, 'name')
      const johnFiatId = await buy(fiatId, johnId)
      const purchases = await purchasesByIds([johnFiatId])
      expect(purchases).to.be.lengthOf(1)
      await updateOwner(johnId, 'SKIP')
      const all = await allPurchases()

      // Owner name was updated in the CarOwnerReadModel
      const newJohnOwner = await waitForId(johnId.toString(), 'CarOwnerReadModel', undefined, 'name')
      expect(originalJohnOwner.data.CarOwnerReadModel.name).to.not.be.eql(newJohnOwner.data.CarOwnerReadModel.name)

      // Owner name was NOT updated in the Purchases read model
      all.map((p) => p.carOwner.name).forEach((name) => expect(name).to.not.eql('SKIP'))
      const purchaseWithOriginalJohnOwner = all
        .map((p) => p.carOwner.name)
        .filter((name) => name === originalJohnOwner.data.CarOwnerReadModel.name)
      expect(purchaseWithOriginalJohnOwner.length).to.be.eql(1)
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

async function addModel(name?: string, brand?: string): Promise<UUID> {
  const mockModelId = random.uuid()
  const mockName: string = name ?? random.alphaNumeric(10)
  const mockBrand: string = brand ?? random.alphaNumeric(10)

  await client.mutate({
    variables: {
      id: mockModelId,
      name: mockName,
      brand: mockBrand,
    },
    mutation: gql`
      mutation AddCarModel($id: ID!, $name: String!, $brand: String!) {
        AddCarModel(input: { id: $id, name: $name, brand: $brand })
      }
    `,
  })
  await getCarModelByIdAndName(mockModelId, mockName)
  return mockModelId
}

async function addOwner(name?: string): Promise<UUID> {
  const mockOwnerId = random.uuid()
  const mockName: string = name ?? random.alphaNumeric(10)

  await client.mutate({
    variables: {
      id: mockOwnerId,
      name: mockName,
    },
    mutation: gql`
      mutation AddCarOwner($id: ID!, $name: String!) {
        AddCarOwner(input: { id: $id, name: $name })
      }
    `,
  })
  await getCarOwnerByIdAndName(mockOwnerId, mockName)
  return mockOwnerId
}

async function addOffer(purchaseIds: Array<string>, name?: string): Promise<UUID> {
  const mockOfferId = random.uuid()
  const mockName: string = name ?? random.alphaNumeric(10)

  await client.mutate({
    variables: {
      id: mockOfferId,
      name: mockName,
      purchaseIds: purchaseIds,
    },
    mutation: gql`
      mutation AddPurchaseOffer($id: ID!, $name: String!, $purchaseIds: [String!]!) {
        AddPurchaseOffer(input: { id: $id, name: $name, purchaseIds: $purchaseIds })
      }
    `,
  })
  await getCarOfferByIdAndName(mockOfferId, mockName)
  return mockOfferId
}

async function buy(modelId: UUID, ownerId: UUID) {
  const mockBuyId = random.uuid()
  await client.mutate({
    variables: {
      id: mockBuyId,
      modelId: modelId,
      ownerId: ownerId,
    },
    mutation: gql`
      mutation BuyCar($id: ID!, $modelId: ID!, $ownerId: ID!) {
        BuyCar(input: { id: $id, modelId: $modelId, ownerId: $ownerId })
      }
    `,
  })
  await waitForId(mockBuyId, 'CarPurchasesReadModel')
  return mockBuyId
}

async function purchasesByIds(ids: Array<UUID>): Promise<Array<any>> {
  const result = await waitForIt(
    () => {
      return client.query({
        variables: {
          filter: {
            id: { in: [...ids] },
          },
        },
        query: gql`
          query ListCarPurchasesReadModels($filter: ListCarPurchasesReadModelFilter) {
            ListCarPurchasesReadModels(filter: $filter) {
              items {
                id
                carModel {
                  id
                  name
                  brand
                }
                carOwner {
                  id
                  name
                }
              }
            }
          }
        `,
      })
    },
    (result) => result?.data?.ListCarPurchasesReadModels.items.length == ids.length
  )
  return result.data.ListCarPurchasesReadModels.items
}

async function allPurchases(): Promise<Array<any>> {
  const result = await client.query({
    variables: {
      filter: {},
    },
    query: gql`
      query ListCarPurchasesReadModels($filter: ListCarPurchasesReadModelFilter) {
        ListCarPurchasesReadModels(filter: $filter) {
          items {
            id
            carModel {
              id
              name
              brand
            }
            carOwner {
              id
              name
            }
          }
        }
      }
    `,
  })

  return result.data.ListCarPurchasesReadModels.items
}

async function updateModel(id: UUID, name?: string): Promise<UUID> {
  const mockName: string = name ?? random.alphaNumeric(10)

  await client.mutate({
    variables: {
      id: id,
      name: mockName,
    },
    mutation: gql`
      mutation UpdateCarModelName($id: ID!, $name: String!) {
        UpdateCarModelName(input: { id: $id, name: $name })
      }
    `,
  })
  await getCarModelByIdAndName(id, mockName)
  return id
}

async function updateOwner(id: UUID, name?: string): Promise<UUID> {
  const mockName: string = name ?? random.alphaNumeric(10)

  await client.mutate({
    variables: {
      id: id,
      name: mockName,
    },
    mutation: gql`
      mutation UpdateCarOwnerName($id: ID!, $name: String!) {
        UpdateCarOwnerName(input: { id: $id, name: $name })
      }
    `,
  })
  await getCarOwnerByIdAndName(id, mockName)
  return id
}

async function getCarModelByIdAndName(id: UUID, modelName: string) {
  return await waitForId(
    id.toString(),
    'CarModelReadModel',
    (result) => {
      const readModel = result?.data.CarModelReadModel
      return readModel && readModel.name === modelName
    },
    'name'
  )
}

async function getCarOfferByIdAndName(id: UUID, offerName: string) {
  return await waitForId(
    id.toString(),
    'CarOfferReadModel',
    (result) => {
      const readModel = result?.data.CarOfferReadModel
      return readModel && readModel.name === offerName
    },
    'name'
  )
}

async function getCarOfferById(id: UUID) {
  return await waitForId(id.toString(), 'CarOfferReadModel', undefined, 'name purchasesIds')
}

async function getPurchaseById(id: UUID) {
  return await waitForId(
    id.toString(),
    'CarPurchasesReadModel',
    undefined,
    'carModel { id name brand } carOwner { id name } offers { id name purchasesIds } '
  )
}

async function getCarOwnerByIdAndName(id: UUID, ownerName: string) {
  return await waitForId(
    id.toString(),
    'CarOwnerReadModel',
    (result) => {
      const readModel = result?.data.CarOwnerReadModel
      return readModel && readModel.name === ownerName
    },
    'name'
  )
}

async function waitForId(
  expectedId: string,
  readModelName: string,
  extraCheck?: (result: ApolloQueryResult<any>) => void,
  extraField?: string
): Promise<any> {
  return await waitForIt(
    () => {
      return client.query({
        variables: {
          id: expectedId,
        },
        query: gql`
                query ${readModelName}($id: ID!) {
                  ${readModelName}(id: $id) {
                    id
                    ${extraField ? extraField : ''}
                  }
                }
              `,
      })
    },
    (result) => {
      const readModel = result?.data[readModelName]
      if (extraCheck) {
        return readModel && readModel.id === expectedId && extraCheck(result)
      }
      return readModel && readModel.id === expectedId
    }
  )
}

function expectModelNamesUpdated(allPurchases: Array<any>, purchaseId: string, updatedPurchases: Array<any>): void {
  const firstPurchase = getFirstPurchase(allPurchases, purchaseId)
  const firstUpdatedPurchase = getFirstPurchase(updatedPurchases, purchaseId)
  // ids are the same
  expect(firstPurchase.id).to.be.eq(firstUpdatedPurchase.id, 'same ids')
  expect(firstPurchase.carModel.id).to.be.eq(firstUpdatedPurchase.carModel.id, 'same carModel ids')
  // and Names were updated
  expect(firstPurchase.carModel.name).to.not.be.eq(firstUpdatedPurchase.carModel.name, 'distinct carModel names')
}

function expectFirstOwnerNamesUpdated(
  allPurchases: Array<any>,
  purchaseId: string,
  updatedPurchases: Array<any>
): void {
  const firstPurchase = getFirstPurchase(allPurchases, purchaseId)
  const firstUpdatedPurchase = getFirstPurchase(updatedPurchases, purchaseId)
  // ids are the same
  expect(firstPurchase.id).to.be.eq(firstUpdatedPurchase.id, 'same ids')
  expect(firstPurchase.carOwner.id).to.be.eq(firstUpdatedPurchase.carOwner.id, 'same carOwner ids')
  // and Names were updated
  expect(firstPurchase.carOwner.name).to.not.be.eq(firstUpdatedPurchase.carOwner.name, 'distinct carOwner names')
}

function expectFirstOwnerNamesNotUpdated(
  allPurchases: Array<any>,
  purchaseId: string,
  updatedPurchases: Array<any>
): void {
  const firstPurchase = getFirstPurchase(allPurchases, purchaseId)
  const firstUpdatedPurchase = getFirstPurchase(updatedPurchases, purchaseId)
  // ids are the same
  expect(firstPurchase.id).to.be.eq(firstUpdatedPurchase.id, 'same ids')
  expect(firstPurchase.carOwner.id).to.be.eq(firstUpdatedPurchase.carOwner.id, 'same carOwner ids')
  // and Names were NOT updated
  expect(firstPurchase.carOwner.name).to.be.eq(firstUpdatedPurchase.carOwner.name, 'same carOwner names')
}

function getFirstPurchase(purchases: Array<any>, purchaseId: string) {
  return purchases.filter((purchase) => purchase.id === purchaseId)[0]
}
