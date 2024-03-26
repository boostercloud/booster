import { expect } from '../../helper/expect'
import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
import { random } from 'faker'
import { waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'

describe('events', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = applicationUnderTest.graphql.client()
  })

  it('should be persisted when flush is call', async () => {
    const mockCartId = random.uuid()
    const result = await waitForIt(
      () =>
        client.mutate({
          variables: {
            cartId: mockCartId,
          },
          mutation: gql`
            mutation FlushEvents($cartId: ID!) {
              FlushEvents(input: { cartId: $cartId, previousProducts: 1, afterProducts: 3 }) {
                id
                cartItems {
                  productId
                  quantity
                }
              }
            }
          `,
        }),
      (result) => result?.data?.FlushEvents != null && result?.data?.FlushEvents.length > 0
    )

    expect(result).not.to.be.null
    const previousProducts = result?.data?.FlushEvents[0].cartItems
    const afterProducts = result?.data?.FlushEvents[1].cartItems

    // Events return the cart flushed the first time
    expect(previousProducts.length).to.be.eq(1)

    // Events doesn't return the last 3 events that were not flushed
    expect(afterProducts.length).to.be.eq(1)

    const queryResult = await waitForIt(
      () => {
        return client.query({
          variables: {
            filter: {
              id: { eq: mockCartId },
            },
          },
          query: gql`
            query ListCartReadModels($filter: ListCartReadModelFilter) {
              ListCartReadModels(filter: $filter) {
                items {
                  id
                  cartItems {
                    productId
                    quantity
                  }
                }
              }
            }
          `,
        })
      },
      (result) => {
        return (
          result?.data?.ListCartReadModels?.items.length === 1 &&
          result?.data?.ListCartReadModels?.items[0].cartItems.length === 4
        )
      }
    )

    // After the command is executed, the register is flushed, so we will have the 4 cartItems
    expect(queryResult.data.ListCartReadModels?.items[0].cartItems.length).to.be.eq(4)
  })

  it('should create an event in the event store', async () => {
    const eventsCount = await waitForIt(
      () => applicationUnderTest.count.events(),
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
        mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
          ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeCartItem).to.be.true

    // Verify number of events
    const expectedEventItemsCount = eventsCount + 1
    await waitForIt(
      () => applicationUnderTest.count.events(),
      (newEventsCount) => newEventsCount === expectedEventItemsCount
    )

    // Verify latest event
    const latestEvent: Array<any> = await applicationUnderTest.query.events(`Cart-${mockCartId}-event`)
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
      () => applicationUnderTest.count.events(),
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
      () => applicationUnderTest.count.events(),
      (newEventsCount) => {
        console.log('Current count', newEventsCount, '\nExpected', expectedEventItemsCount)
        return newEventsCount >= expectedEventItemsCount
      }
    )

    // Verify latest events
    const latestEvents: Array<any> = await applicationUnderTest.query.events(`Cart-${mockCartId}-event`)
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

  it('should create multiple events in the event store in multiple batches', async () => {
    const eventsCount = await waitForIt(
      () => applicationUnderTest.count.events(),
      (eventsCount) => eventsCount > 0
    )

    const firstMockCartId = random.uuid()
    const secondMockCartId = random.uuid()
    const firstEventsToCreate = 101 // Using 100 because batches are of 101, making sure the batching gets triggered
    const secondEventsToCreate = 50 // Using 50 more to make sure the batching gets triggered per primary key
    const response = await client.mutate({
      variables: {
        items: [
          {
            cartId: firstMockCartId,
            itemsCount: firstEventsToCreate,
          },
          {
            cartId: secondMockCartId,
            itemsCount: secondEventsToCreate,
          },
        ],
      },
      mutation: gql`
        mutation ChangeMultipleCartItemsWithIds($items: [JSON!]!) {
          ChangeMultipleCartItemsWithIds(input: { items: $items })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.ChangeMultipleCartItemsWithIds).to.be.true

    // Verify number of events
    const expectedEventItemsCount = eventsCount + firstEventsToCreate + secondEventsToCreate
    await waitForIt(
      () => applicationUnderTest.count.events(),
      (newEventsCount) => {
        console.log('Current count', newEventsCount, '\nExpected', expectedEventItemsCount)
        return newEventsCount >= expectedEventItemsCount
      }
    )

    await checkLatestEvents(firstMockCartId, firstEventsToCreate)
    await checkLatestEvents(secondMockCartId, secondEventsToCreate)
  })
})

async function checkLatestEvents(cartId: string, eventsToCreate: number): Promise<void> {
  const primaryKey = `Cart-${cartId}-event`
  const events: Array<any> = await applicationUnderTest.query.events(primaryKey)
  expect(events).not.to.be.null
  const eventProductIds: Array<number> = []
  for (const event of events) {
    expect(event.entityTypeName_entityID_kind).to.be.equal(primaryKey)
    const productIdNumber = parseInt(event.value.productId)
    expect(productIdNumber).to.be.gte(0)
    expect(productIdNumber).to.be.lessThan(eventsToCreate)
    expect(event.value.cartId).to.be.equal(cartId)
    expect(event.value.quantity).to.be.equal(1)
    expect(event.kind).to.be.equal('event')
    expect(event.entityTypeName).to.be.equal('Cart')
    expect(event.typeName).to.be.equal('CartItemChanged')
    eventProductIds.push(productIdNumber)
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const secondExpectEvents = expect(eventProductIds) as any
  secondExpectEvents.to.be.sorted((prev: number, next: number) => prev > next)
}
