import { expect } from '../../helper/expect'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { random } from 'faker'
import { waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'

describe('notifications', async () => {
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
            mutation FlushNotifications($cartId: ID!) {
              FlushNotifications(input: { cartId: $cartId, previousProducts: 1, afterProducts: 3 }) {
                id
                cartItems {
                  productId
                  quantity
                }
              }
            }
          `,
        }),
      (result) => result?.data?.FlushNotifications != null && result?.data?.FlushNotifications.length > 0
    )

    expect(result).not.to.be.null
    const previousProducts = result?.data?.FlushNotifications[0].cartItems
    const afterProducts = result?.data?.FlushNotifications[1].cartItems

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
                  checks
                }
              }
            }
          `,
        })
      },
      (result) => {
        const isDefined = result?.data?.ListCartReadModels?.items?.[0]?.checks !== undefined
        const notZero = result?.data?.ListCartReadModels?.items?.[0]?.checks !== 0
        return isDefined && notZero
      }
    )

    // After the command is executed, the register is flushed, so we will have the 4 cartItems
    expect(queryResult.data.ListCartReadModels?.checks).to.eq(4)
  })

  it('should create an event in the event store', async () => {
    const eventsCount = await waitForIt(
      () => applicationUnderTest.count.events(),
      (eventsCount) => eventsCount > 0
    )

    const mockCartId = random.uuid()
    const response = await client.mutate({
      variables: {
        cartId: mockCartId,
      },
      mutation: gql`
        mutation AbandonCart($cartId: ID!) {
          AbandonCart(input: { cartId: $cartId })
        }
      `,
    })

    expect(response).not.to.be.null
    expect(response?.data?.AbandonCart).to.be.true

    // Verify number of events
    const expectedEventItemsCount = eventsCount + 1
    await waitForIt(
      () => applicationUnderTest.count.events(),
      (newEventsCount) => newEventsCount === expectedEventItemsCount
    )

    // Verify latest event
    const latestEvent: Array<any> = await applicationUnderTest.query.events(`defaultTopic-${mockCartId}-event`)
    expect(latestEvent).not.to.be.null
    expect(latestEvent).not.to.be.empty

    expect(latestEvent[0].entityTypeName_entityID_kind).to.be.equal(`defaultTopic-${mockCartId}-event`)
    expect(latestEvent[0].value.something).to.be.equal(mockCartId)
    expect(latestEvent[0].kind).to.be.equal('event')
    expect(latestEvent[0].entityTypeName).to.be.equal('defaultTopic')
    expect(latestEvent[0].typeName).to.be.equal('CartAbandoned')
  })
})
