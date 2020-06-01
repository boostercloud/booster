import { countEventItems, countSnapshotItems, queryEvents, graphQLClient, waitForIt } from './utils'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'

describe('the commands API', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
  })

  it('accepts a command successfully', async () => {
    const response = await client.mutate({
      variables: {
        cartId: random.uuid(),
        productId: random.uuid(),
        quantity: random.number({ min: 1 }),
      },
      mutation: gql`
        mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
          ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeCartItem).to.be.true
  })

  it('should create an event in the event store', async () => {
    const eventsCount = await waitForIt(
      () => countEventItems(),
      (eventsCount) => eventsCount > 0
    )

    const mockCartId = random.uuid()
    const mockProductId = random.uuid()
    const mockQuantity = random.number({ min: 1 })
    const response = await client.mutate({
      variables: {
        cartId: mockCartId,
        productId: mockProductId,
        quantity: mockQuantity,
      },
      mutation: gql`
        mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
          ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeCartItem).to.be.true

    // Verify number of events
    const expectedEventItemsCount = eventsCount + 1
    await waitForIt(
      () => countEventItems(),
      (newEventsCount) => newEventsCount === expectedEventItemsCount
    )

    // Verify latest event
    const latestEvent = await queryEvents(`Cart-${mockCartId}-event`)
    expect(latestEvent).not.to.be.null

    expect(latestEvent[0].entityTypeName_entityID_kind).to.be.equal(`Cart-${mockCartId}-event`)
    expect(latestEvent[0].value.productId).to.be.equal(mockProductId)
    expect(latestEvent[0].value.cartId).to.be.equal(mockCartId)
    expect(latestEvent[0].value.quantity).to.be.equal(mockQuantity)
    expect(latestEvent[0].kind).to.be.equal('event')
    expect(latestEvent[0].entityTypeName).to.be.equal('Cart')
    expect(latestEvent[0].typeName).to.be.equal('ChangedCartItem')
  })

  it('should generate a snapshot after 5 events with the same entity id', async () => {
    const snapshotsCount = await countSnapshotItems()

    const commandsPromises: Promise<any>[] = []

    const mockCartId = random.uuid()
    const mockProductId = random.uuid()

    for (let i = 0; i < 5; i++) {
      commandsPromises.push(
        client.mutate({
          variables: {
            cartId: mockCartId,
            productId: mockProductId,
            quantity: i,
          },
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
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

    const expectedSnapshotItemsCount = snapshotsCount + 1
    await waitForIt(
      () => countSnapshotItems(),
      (newSnapshotsCount) => newSnapshotsCount === expectedSnapshotItemsCount
    )
  })
})
