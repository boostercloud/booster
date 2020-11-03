import { graphQLClient, waitForIt } from '../utils'
import gql from 'graphql-tag'
import { expect } from 'chai'

describe('Scheduled commands', () => {
  it('scheduled command ran and created a product', async () => {
    const client = await graphQLClient()
    const checkedCartSku = 'the-checked-cart'

    // Check that scheduled command created the new product
    const cartData = await waitForIt(
      () => {
        return client.query({
          query: gql`
            query {
              CartReadModel(id: "${checkedCartSku}") {
                id
                checks
              }
            }
          `,
        })
      },
      (result) => !!result?.data?.CartReadModel
    )

    const cartReadModel = cartData?.data?.CartReadModel
    expect(cartReadModel).not.to.be.null
    expect(cartReadModel.id).to.equal(checkedCartSku)
    expect(cartReadModel.checks).to.be.greaterThan(0)
  })
})
