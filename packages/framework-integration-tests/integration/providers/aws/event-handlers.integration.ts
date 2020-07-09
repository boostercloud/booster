import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { createUser, getAuthToken, getEventsByEntityId, graphQLClient, waitForIt } from './utils'
import { random, address, internet } from 'faker'
import { expect } from 'chai'

describe('Event handlers', () => {
  let adminEmail: string
  const adminPassword = 'Enable_G0d_Mode3e!'

  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    adminEmail = internet.email()
    await createUser(adminEmail, adminPassword, 'Admin')
    const authToken = await getAuthToken(adminEmail, adminPassword)

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

      const events: Array<any> = await waitForIt(
        () => getEventsByEntityId(mockProductId),
        (events) => {
          return events?.length > 1
        }
      )

      expect(events.length).to.be.equal(2)

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
          email: adminEmail,
          roles: ['Admin'],
        },
      }
      const stockMovedEvent = events.find((event) => event.typeName === 'StockMoved')
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
          email: adminEmail,
          roles: ['Admin'],
        },
      }
      const productAvailabilityChangedEvent = events.find((event) => event.typeName === 'ProductAvailabilityChanged')
      expect(productAvailabilityChangedEvent).to.deep.contain(expectedProductAvailabilityChangedEvent)
    })
  })
})
