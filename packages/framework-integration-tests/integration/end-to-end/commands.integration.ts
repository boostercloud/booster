import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient } from '../providers/aws/utils'
import { random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'

describe('Commands end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
  })

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

  describe('AddPriceToInvoice command', () => {
    it('should retrieve both the newest snapshot and a specific one based on a date', async () => {
      const id = '122333444455555'
      const totalPrice = 5
      for (let i = 1; i <= 11; i++) {
        const createdAt = new Date(i).toISOString()
        await client.mutate({
          variables: {
            id: id,
            totalPrice: totalPrice,
            invoiceFinished: i === 11, // We get snapshots on the 11th one, when 2 snapshots have been generated already
            createdAt: createdAt,
          },
          mutation: gql`
            mutation AddPriceToInvoice($id: ID!, $totalPrice: Float, $invoiceFinished: Boolean, $createdAt: String) {
              AddPriceToInvoice(
                input: { id: $id, totalPrice: $totalPrice, invoiceFinished: $invoiceFinished, createdAt: $createdAt }
              )
            }
          `,
        })
      }
    })
  })
})
