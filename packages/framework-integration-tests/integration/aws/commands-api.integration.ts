import { countEventItems, countSnapshotItems, eventsStoreTableName, graphQLClient, sleep } from './utils'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'

describe('the commands API', async () => {
  let client: ApolloClient<NormalizedCacheObject>
  let eventStoreTableName: string

  before(async () => {
    client = await graphQLClient()
    eventStoreTableName = await eventsStoreTableName()
  })

  it('accepts a command successfully', async () => {
    const response = await client.mutate({
      mutation: gql`
        mutation {
          ChangeCartItem(input: { cartId: "demo-cart-id", productId: "demo-product-id", quantity: 5 })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeCartItem).to.be.true

    // Let some time to create the event and update the read model
    await sleep(5000)
  })

  it('should create an event in the event store', async () => {
    const eventsCount = await countEventItems(eventStoreTableName)
    expect(eventsCount).to.be.greaterThan(0)

    const response = await client.mutate({
      mutation: gql`
        mutation {
          ChangeCartItem(input: { cartId: "demo-cart-id", productId: "demo-product-id", quantity: 2 })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeCartItem).to.be.true

    await sleep(5000)

    const expectedEventItemsCount = eventsCount + 1
    expect(await countEventItems(eventStoreTableName)).to.be.equal(expectedEventItemsCount)
  })

  it('should generate a snapshot after 5 events', async () => {
    const snapshotsCount = await countSnapshotItems(eventStoreTableName)

    for (let i = 0; i < 5; i++) {
      const response = await client.mutate({
        mutation: gql`
          mutation {
            ChangeCartItem(input: { cartId: "demo-cart-id", productId: "demo-product-id", quantity: 2 })
          }
        `,
      })

      expect(response).not.to.be.null
      expect(response?.data?.ChangeCartItem).to.be.true
    }

    await sleep(5000)

    const expectedSnapshotItemsCount = snapshotsCount + 1
    expect(await countSnapshotItems(eventStoreTableName)).to.be.equal(expectedSnapshotItemsCount)
  })
})
