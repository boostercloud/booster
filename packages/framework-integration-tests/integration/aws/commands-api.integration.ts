import { countEventItems, countSnapshotItems, eventsStoreTableName, queryEvents, graphQLClient, sleep } from './utils'
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
          ChangeCartItem(input: { cartId: "demo-cart-id", productId: "demo-product-id", quantity: 1 })
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

    // Verify number of events
    const expectedEventItemsCount = eventsCount + 1
    expect(await countEventItems(eventStoreTableName)).to.be.equal(expectedEventItemsCount)

    // Verify latest event
    const latestEvent = await queryEvents(eventStoreTableName, 'Cart-demo-cart-id-event')
    expect(latestEvent).not.to.be.null
    expect(latestEvent[0].entityTypeName_entityID_kind).to.be.equal('Cart-demo-cart-id-event')
    expect(latestEvent[0].value.productId).to.be.equal('demo-product-id')
    expect(latestEvent[0].value.cartId).to.be.equal('demo-cart-id')
    expect(latestEvent[0].value.quantity).to.be.equal(2)
    expect(latestEvent[0].kind).to.be.equal('event')
    expect(latestEvent[0].entityTypeName).to.be.equal('Cart')
    expect(latestEvent[0].typeName).to.be.equal('ChangedCartItem')
  })

  it('should generate a snapshot after 5 events', async () => {
    const snapshotsCount = await countSnapshotItems(eventStoreTableName)

    const eventsPromises: Promise<any>[] = []
    for (let i = 0; i < 5; i++) {
      eventsPromises.push(
        client.mutate({
          mutation: gql`
            mutation {
              ChangeCartItem(input: { cartId: "demo-cart-id", productId: "demo-product-id", quantity: 3 })
            }
          `,
        })
      )
    }

    const changeCartItemResults = await Promise.all(eventsPromises)

    changeCartItemResults.forEach((response: any) => {
      expect(response).not.to.be.null
      expect(response?.data?.ChangeCartItem).to.be.true
    })

    await sleep(5000)

    const expectedSnapshotItemsCount = snapshotsCount + 1
    expect(await countSnapshotItems(eventStoreTableName)).to.be.equal(expectedSnapshotItemsCount)
  })
})
