import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient } from './utils'
import gql from 'graphql-tag'
import { random } from 'faker'
import { expect } from 'chai'

describe('commands', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
  })

  it('should successfully process a command', async () => {
    const mockCartId = random.uuid()
    const mockProductId = random.uuid()
    const mockQuantity = random.number({ min: 1 })

    const mutationResult = await client.mutate({
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

    expect(mutationResult).not.to.be.null
    expect(mutationResult.data.ChangeCartItem).to.be.true
  })
})
