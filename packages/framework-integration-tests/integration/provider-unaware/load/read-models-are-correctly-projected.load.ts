import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { internet, random } from 'faker'
import { expect } from '../../helper/expect'
import gql from 'graphql-tag'

import { waitForIt } from '../../helper/sleep'
import { applicationUnderTest, scriptExecutor } from './setup'

describe('Optimistic concurrency on read models', () => {
  let client: ApolloClient<NormalizedCacheObject>
  let token: string

  before(async () => {
    const userEmail = internet.email()
    token = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail')
    client = applicationUnderTest.graphql.client(token)
  })

  context('with 400 products on the same SKU', () => {
    it('processes the events without corrupting read models data', async () => {
      const duration = 2
      const arrivalRate = 200
      const expectedProductsBySku = duration * arrivalRate
      const sku = `ABC_${random.uuid()}`
      await scriptExecutor.executeScript('create-products-same-sku.yml', {
        variables: { token, sku },
        phases: [{ duration, arrivalRate }],
      })

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
        (result) => {
          const currentProducts = result.data.ProductsBySKU?.products.length
          console.debug(`Products with the same SKU. Got: ${currentProducts}, expected: ${expectedProductsBySku}`)
          return currentProducts === expectedProductsBySku
        }
      )
      expect(result.data.ProductsBySKU.id).to.be.equal(sku)
      expect(result.data.ProductsBySKU.products.length).to.be.equal(expectedProductsBySku)
    })
  })
})
