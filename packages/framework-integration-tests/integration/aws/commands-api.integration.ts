import { graphQLClient } from './utils'
import { expect } from 'chai'
import gql from 'graphql-tag'

describe('the commands API', () => {
  it('accepts a command successfully', async () => {
    const client = await graphQLClient()

    const response = await client.mutate({
      mutation: gql`
        mutation {
          ChangeCartItem(input: { cartId: "demo", productId: "123", quantity: 2 })
        }
      `,
    })

    // TODO: We can't reach this point yet because there are pending bugs to be solved in the GraphQL implementation
    expect(response).not.to.be.null

    // TODO: Check that the event was submitted successfully (We probably want to do the change to remove Kinesis before working too much on this)
    // TODO: Wait enough time and check that the event was inserted in the event store

  }).skip
})
