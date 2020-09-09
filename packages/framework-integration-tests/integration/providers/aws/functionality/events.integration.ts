import { countEventItems, countSnapshotItems, queryEvents, graphQLClient, waitForIt } from '../utils'
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'

describe('events', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
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
    expect(latestEvent[0].typeName).to.be.equal('CartItemChanged')
  })

  it('should generate a snapshot after 5 events with the same entity id', async () => {
    const mockCartId = random.uuid()
    const mockProductId = random.uuid()

    const snapshotsCount = await countSnapshotItems('Cart', mockCartId)
    expect(snapshotsCount).to.be.equal(0)

    for (let i = 0; i < 5; i++) {
      const response = await client.mutate({
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

      expect(response).not.to.be.null
      expect(response?.data?.ChangeCartItem).to.be.true
    }

    const minSnapshotItemsCount = 1
    const newSnapshotItemsCount = await waitForIt(
      () => countSnapshotItems('Cart', mockCartId),
      (newSnapshotsCount) => newSnapshotsCount >= minSnapshotItemsCount
    )
    expect(newSnapshotItemsCount).to.be.equal(minSnapshotItemsCount)
  })
})
