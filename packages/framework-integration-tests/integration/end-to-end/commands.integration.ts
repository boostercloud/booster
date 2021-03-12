import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient} from '../providers/aws/utils'
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
})
