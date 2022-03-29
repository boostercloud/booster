import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { applicationUnderTest } from './setup'

describe('Commands end-to-end tests', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await applicationUnderTest.graphql.client()
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

  it('accepts an empty command', async () => {
    const response = await client.mutate({
      variables: {},
      mutation: gql`
        mutation {
          EmptyCommand
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.EmptyCommand).to.be.equal('Empty command executed')
  })
})
