import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient, getTokenForUser, queryEvents } from '../utils'
import { random, address, internet } from 'faker'
import { expect } from 'chai'
import { waitForIt } from '../../../helper/sleep'

describe('Event handlers', () => {
  let adminEmail: string

  let authToken: string
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    adminEmail = internet.email()
    authToken = await getTokenForUser(adminEmail, 'Admin')
    client = await graphQLClient(authToken)
  })

  context('move product', () => {
    it('should trigger a new ProductAvailabilityChanged event', async () => {
      const mockProductId: string = random.uuid()
      const mockOrigin = 'provider'
      const mockDestination: string = address.city()
      const mockQuantity: number = random.number({ min: 1 })

      await client.mutate({
        variables: {
          productID: mockProductId,
          origin: mockOrigin,
          destination: mockDestination,
          quantity: mockQuantity,
        },
        mutation: gql`
          mutation MoveStock($productID: String!, $origin: String, $destination: String, $quantity: Float) {
            MoveStock(input: { productID: $productID, origin: $origin, destination: $destination, quantity: $quantity })
          }
        `,
      })

      const stockEvents: Array<any> = await waitForIt(
        () => queryEvents(`Stock-${mockProductId}-event`),
        (events) => {
          return events?.length === 1
        }
      )
      const productEvents: Array<any> = await waitForIt(
        () => queryEvents(`Product-${mockProductId}-event`),
        (events) => {
          return events?.length === 1
        }
      )

      const expectedStockMovedEvent = {
        // eslint-disable-next-line @typescript-eslint/camelcase
        entityTypeName_entityID_kind: `Stock-${mockProductId}-event`,
        version: 1,
        value: {
          destination: mockDestination,
          quantity: mockQuantity,
          productID: mockProductId,
          origin: mockOrigin,
        },
        kind: 'event',
        entityTypeName: 'Stock',
        typeName: 'StockMoved',
        entityID: mockProductId,
        currentUser: {
          username: adminEmail,
          role: 'Admin',
          id: adminEmail,
        },
      }
      const stockMovedEvent = stockEvents[0]
      expect(stockMovedEvent).to.deep.contain(expectedStockMovedEvent)

      const expectedProductAvailabilityChangedEvent = {
        // eslint-disable-next-line @typescript-eslint/camelcase
        entityTypeName_entityID_kind: `Product-${mockProductId}-event`,
        version: 1,
        value: {
          productID: mockProductId,
          quantity: mockQuantity,
        },
        kind: 'event',
        entityTypeName: 'Product',
        typeName: 'ProductAvailabilityChanged',
        entityID: mockProductId,
        currentUser: {
          username: adminEmail,
          role: 'Admin',
          id: adminEmail,
        },
      }
      const productAvailabilityChangedEvent = productEvents[0]
      expect(productAvailabilityChangedEvent).to.deep.contain(expectedProductAvailabilityChangedEvent)
    })
  })
})
