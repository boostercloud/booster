import { countReadModelItems, graphQLClient, queryReadModels} from '../utils'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'
import { waitForIt } from '../../../helper/sleep'

const CART_READ_MODEL_NAME = 'CartReadModel'

describe('entities', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
  })

  it('should be projected into a read model', async () => {
    const readModelItemsCount = await countReadModelItems(CART_READ_MODEL_NAME)

    const mockCartId = random.uuid()
    const mockProductId = random.uuid()
    const mockQuantity = random.number({ min: 1 })
    const mockPaymentId: string = random.uuid()
    const mockConfirmationToken: string = random.alphaNumeric(10)

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
      () => queryReadModels(mockCartId, CART_READ_MODEL_NAME),
      (readModel) =>
        readModel && readModel[0]?.id === mockCartId && readModel[0]?.cartItems[0]?.productId === mockProductId
    )

    const ConfirmPaymentResponse = await client.mutate({
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

    expect(ConfirmPaymentResponse).not.to.be.null
    expect(ConfirmPaymentResponse?.data?.ConfirmPayment).to.be.true

    const expectedReadModelItemsCount = readModelItemsCount + 1
    await waitForIt(
      () => countReadModelItems(CART_READ_MODEL_NAME),
      (newReadModelItemsCount) => newReadModelItemsCount === expectedReadModelItemsCount
    )

    const latestReadModelItem = await waitForIt(
      () => queryReadModels(mockCartId, CART_READ_MODEL_NAME),
      (readModel) =>
        readModel &&
        readModel[0]?.id === mockCartId &&
        readModel[0]?.payment &&
        readModel[0]?.cartItems[0]?.productId === mockProductId
    )

    expect(latestReadModelItem).not.to.be.null
    expect(latestReadModelItem[0].id).to.be.equal(mockCartId)
    expect(latestReadModelItem[0].cartItems[0].productId).to.be.equal(mockProductId)
    expect(latestReadModelItem[0].cartItems[0].quantity).to.be.equal(mockQuantity)
    expect(latestReadModelItem[0].cartItems[0].shippingAddress).to.be.undefined
    expect(latestReadModelItem[0].payment.id).to.be.equal(mockPaymentId)
    expect(latestReadModelItem[0].payment.cartId).to.be.equal(mockCartId)
    expect(latestReadModelItem[0].payment.confirmationToken).to.be.equal(mockConfirmationToken)
  })
})
