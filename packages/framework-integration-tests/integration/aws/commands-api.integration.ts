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
    const eventsCount = await countEventItems()
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
    expect(await countEventItems()).to.be.equal(expectedEventItemsCount)

    // Verify latest event
    const latestEvent = await queryEvents('Cart-demo-cart-id-event')
    expect(latestEvent).not.to.be.null
    const expectedEvent: EventEnvelope = {
       entityTypeName_entityID_kind: 'Cart-demo-cart-id-event'
       value: {
         productId: 'demo-product-id'
         cartId: 'demo-cart-id'
         quantity: 2
       }
       kind: 'event'
       entityTypeName: 'Cart'
       typeName: 'ChangedCartItem'
    })
    expect(latestEvent[0]).to.be.deep.equal(expectedEvent)
  })

  it('should generate a snapshot after 5 events', async () => {
    const snapshotsCount = await countSnapshotItems()

    const commandsPromises: Promise<any>[] = []
    for (let i = 0; i < 5; i++) {
      commandsPromises.push(
        client.mutate({
          mutation: gql`
            mutation {
              ChangeCartItem(input: { cartId: "demo-cart-id", productId: "demo-product-id", quantity: 3 })
            }
          `,
        })
      )
    }

    const changeCartItemResults = await Promise.all(commandsPromises)

    changeCartItemResults.forEach((response: any) => {
      expect(response).not.to.be.null
      expect(response?.data?.ChangeCartItem).to.be.true
    })

    await sleep(5000)

    const expectedSnapshotItemsCount = snapshotsCount + 1
    expect(await countSnapshotItems()).to.be.equal(expectedSnapshotItemsCount)
  })
})
