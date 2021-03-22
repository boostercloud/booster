import { countEventItems, countSnapshotItems, queryEvents, graphQLClient } from '../utils'
import { expect } from '../../../helper/expect'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'
import { waitForIt } from '../../../helper/sleep'

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

  it('should create multiple events in the event store in batches', async () => {
    const eventsCount = await waitForIt(
      () => countEventItems(),
      (eventsCount) => eventsCount > 0
    )

    const mockCartId = random.uuid()
    const eventsToCreate = 30 // Using 30 because batches are of 25, making sure the batching gets triggered
    const response = await client.mutate({
      variables: {
        cartId: mockCartId,
        itemsCount: eventsToCreate,
      },
      mutation: gql`
        mutation ChangeMultipleCartItems($cartId: ID!, $itemsCount: Float!) {
          ChangeMultipleCartItems(input: { cartId: $cartId, itemsCount: $itemsCount })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeMultipleCartItems).to.be.true

    // Verify number of events
    const expectedEventItemsCount = eventsCount + eventsToCreate
    await waitForIt(
      () => countEventItems(),
      (newEventsCount) => {
        console.log('Current count', newEventsCount, '\nExpected', expectedEventItemsCount)
        return newEventsCount >= expectedEventItemsCount
      }
    )

    // Verify latest events
    const latestEvents = await queryEvents(`Cart-${mockCartId}-event`)
    const eventProductIds: Array<number> = []
    expect(latestEvents).not.to.be.null

    for (const event of latestEvents) {
      expect(event.entityTypeName_entityID_kind).to.be.equal(`Cart-${mockCartId}-event`)
      const productIdNumber = parseInt(event.value.productId)
      expect(productIdNumber).to.be.gte(0)
      expect(productIdNumber).to.be.lessThan(eventsToCreate)
      expect(event.value.cartId).to.be.equal(mockCartId)
      expect(event.value.quantity).to.be.equal(1)
      expect(event.kind).to.be.equal('event')
      expect(event.entityTypeName).to.be.equal('Cart')
      expect(event.typeName).to.be.equal('CartItemChanged')
      eventProductIds.push(productIdNumber)
    }

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const expectEvents = expect(eventProductIds) as any
    expectEvents.to.be.sorted((prev: number, next: number) => prev > next)
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
