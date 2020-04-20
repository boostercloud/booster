import { graphQLClient } from './utils'
import { expect } from 'chai'
import gql from 'graphql-tag'

describe('the commands API', () => {
  xit('accepts a command successfully', async () => {
    const client = await graphQLClient()

    const response = await client.mutate({
      mutation: gql`
        mutation {
          ChangeCartItem(input: { cartId: "demo", productId: "123", quantity: 2 })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeCartItem).to.be.true
  })
})
