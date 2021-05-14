import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { internet, random } from 'faker'
import gql from 'graphql-tag'

import { waitForIt } from '../../helper/sleep'
import { applicationUnderTest, scriptExecutor } from './setup'

describe('Data consistency on entities', () => {
  let client: ApolloClient<NormalizedCacheObject>
  let token: string

  before(async () => {
    const userEmail = internet.email()
    token = applicationUnderTest.token.forUser(userEmail, 'Admin')
    client = applicationUnderTest.graphql.client(token)
  })

  context('with 200 products ready to be stocked', () => {
    const idPrefix = random.alpha({ count: 10 })
    const destinationWarehouse = 'GC'
    const numberOfProducts = 1000
    let productIDs: Array<string>

    before(async () => {
      productIDs = []
      for (let i = 0; i < numberOfProducts; i++) {
        productIDs[i] = idPrefix + random.uuid()
      }
    })

    it('adds stock to all of them with many events without corrupting data', async () => {
      const durationWarmup = 10
      const arrivalRateWarmup = 200
      const durationBurst = 10
      const arrivalRateBurst = 2000
      const expectedStock = durationWarmup * arrivalRateWarmup + durationBurst * arrivalRateBurst
      await scriptExecutor.executeScript('move-product-stock.yml', {
        variables: { token, productID: productIDs, destinationWarehouse },
        phases: [
          {
            duration: durationWarmup,
            arrivalRate: arrivalRateWarmup,
          },
          {
            duration: durationBurst,
            arrivalRate: arrivalRateBurst,
          },
        ],
      })

      await waitForIt(
        () => queryStocks(client, idPrefix),
        (result) => {
          const totalStock = result.data.StockReadModels.map(
            (stock: any) => stock.warehouses[destinationWarehouse]
          ).reduce((stockProductA: number, stockProductB: number) => stockProductA + stockProductB, 0)
          console.debug(`Total stock. Got: ${totalStock}, expected: ${expectedStock}`)
          return totalStock === expectedStock
        }
      )
    })
  })
})

async function queryStocks(client: ApolloClient<NormalizedCacheObject>, idPrefix: string): Promise<any> {
  return client.query({
    query: gql`
        query {
            StockReadModels(filter: { id: { beginsWith: "${idPrefix}"}}) {
                id
                warehouses
            }
        }
    `,
  })
}
