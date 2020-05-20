import { countItems, eventsStoreTableName, graphQLClient, sleep } from './utils'
import { expect } from 'chai'
import gql from 'graphql-tag'

describe('the commands API', () => {
  let eventStoreTableName: string

  before(async () => {
    eventStoreTableName = await eventsStoreTableName()
  })

  it('accepts a command successfully', async () => {
    const client = await graphQLClient()

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
    const client = await graphQLClient()

    const numberOfEvents = await countItems(eventStoreTableName)

    expect(numberOfEvents).to.be.greaterThan(0)

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

    const expectedEventItemsCount = numberOfEvents + (numberOfEvents % 5 == 0 ? 2 : 1)
    expect(await countItems(eventStoreTableName)).to.be.equal(expectedEventItemsCount)
  })
})
