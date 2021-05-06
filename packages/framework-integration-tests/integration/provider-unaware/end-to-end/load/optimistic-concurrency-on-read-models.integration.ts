import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { internet, random } from 'faker'
import { expect } from '../../../helper/expect'
import gql from 'graphql-tag'

import { waitForIt } from '../../../helper/sleep'
import { applicationUnderTest } from '../setup'

describe('Optimistic concurrency on read models', () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    const userEmail = internet.email()

    // TODO: Make retrieval of auth token cloud agnostic
    const idToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail')
    client = applicationUnderTest.graphql.client(idToken)
  })

  context('with 250 products on the same SKU', () => {
    const numberOfProductsBySKU = 250

    it('processes the events without corrupting read models data', async () => {
      const sku = `ABC_${random.uuid()}`
      const promises: Array<Promise<unknown>> = []
      for (let i = 0; i < numberOfProductsBySKU; i++) {
        promises.push(
          client.mutate({
            variables: { sku },
            mutation: gql`
              mutation CreateProduct($sku: String!) {
                CreateProduct(input: { sku: $sku })
              }
            `,
          })
        )
      }
      await Promise.all(promises)
      const result = await waitForIt(
        () =>
          client.query({
            variables: { sku },
            query: gql`
              query ProductsBySKU($sku: ID!) {
                ProductsBySKU(id: $sku) {
                  id
                  products
                }
              }
            `,
          }),
        (result) => result.data.ProductsBySKU.products.length === numberOfProductsBySKU
      )
      expect(result.data.ProductsBySKU.id).to.be.equal(sku)
      expect(result.data.ProductsBySKU.products.length).to.be.equal(numberOfProductsBySKU)
    })
  })
})
