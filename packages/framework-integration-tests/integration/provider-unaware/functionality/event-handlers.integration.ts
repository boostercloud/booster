import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random, address, internet, lorem, commerce, finance } from 'faker'
import { expect } from 'chai'
import { waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'

describe('Event handlers', () => {
  let adminEmail: string

  let authToken: string
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    adminEmail = internet.email()
    authToken = applicationUnderTest.token.forUser(adminEmail, 'Admin')
    client = applicationUnderTest.graphql.client(authToken)
  })

  context('move product', () => {
    it('should trigger a new ProductAvailabilityChanged event', async () => {
      const mockProductId: string = random.uuid()
      const mockOrigin = 'provider'
      const mockDestination: string = address.city()
      const mockQuantity: number = random.number({ min: 1 })

      await createProductAndWaitForIt(client, mockProductId)

      await client.mutate({
        variables: {
          productID: mockProductId,
          origin: mockOrigin,
          destination: mockDestination,
          quantity: mockQuantity,
        },
        mutation: gql`
          mutation MoveStock($productID: String!, $origin: String, $destination: String, $quantity: Float!) {
            MoveStock(input: { productID: $productID, origin: $origin, destination: $destination, quantity: $quantity })
          }
        `,
      })

      const stockEvents = await waitForIt(
        () => applicationUnderTest.query.events(`Stock-${mockProductId}-event`),
        (events) => {
          return events?.length === 1
        }
      )
      const productEvents = await waitForIt(
        () => applicationUnderTest.query.events(`Product-${mockProductId}-event`),
        (events) => {
          return events?.length === 2
        }
      )

      const stockMovedEvent = stockEvents[0]

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
          claims: {
            'booster:role': 'Admin',
            email: adminEmail,
            iat: (stockMovedEvent as any).currentUser.claims.iat,
            id: adminEmail,
            iss: 'booster',
            sub: adminEmail,
          },
          header: {
            alg: 'RS256',
            kid: 'booster',
            typ: 'JWT',
          },
          username: adminEmail,
          roles: ['Admin'],
          id: adminEmail,
        },
      }

      expect(stockMovedEvent).to.deep.contain(expectedStockMovedEvent)

      const productAvailabilityChangedEvent = productEvents[0]

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
          claims: {
            'booster:role': 'Admin',
            email: adminEmail,
            iat: (productAvailabilityChangedEvent as any).currentUser.claims.iat,
            id: adminEmail,
            iss: 'booster',
            sub: adminEmail,
          },
          header: {
            alg: 'RS256',
            kid: 'booster',
            typ: 'JWT',
          },
          username: adminEmail,
          roles: ['Admin'],
          id: adminEmail,
        },
      }

      expect(productAvailabilityChangedEvent).to.deep.contain(expectedProductAvailabilityChangedEvent)
    })
  })
})

async function createProductAndWaitForIt(
  client: ApolloClient<NormalizedCacheObject>,
  mockProductId: string
): Promise<void> {
  await client.mutate({
    variables: {
      productID: mockProductId,
      sku: random.alpha({ count: 10 }),
      displayName: commerce.productName(),
      description: lorem.paragraph(),
      priceInCents: random.number({ min: 1 }),
      currency: finance.currencyCode(),
    },
    mutation: gql`
      mutation CreateProduct(
        $productID: ID!
        $sku: String!
        $displayName: String!
        $description: String!
        $priceInCents: Float!
        $currency: String!
      ) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            displayName: $displayName
            description: $description
            priceInCents: $priceInCents
            currency: $currency
          }
        )
      }
    `,
  })
  await waitForIt(
    () => applicationUnderTest.query.events(`Product-${mockProductId}-event`),
    (events) => {
      return events?.length === 1
    }
  )
}
