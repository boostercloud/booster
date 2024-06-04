import { expect } from '../../helper/expect'
import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
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
                checks
              }
            }
          `,
        }),
      (result) => result?.data?.FlushNotifications.length > 0
    )

    expect(result).not.to.be.null
    console.log(JSON.stringify(result))
    const previousChecks = result?.data?.FlushNotifications[0].checks
    const afterChecks = result?.data?.FlushNotifications[1].checks

    // Events return the cart flushed the first time
    expect(previousChecks, 'previous products').to.be.eq(1)

    // Events doesn't return the last 3 events that were not flushed
    expect(afterChecks, 'after products').to.be.eq(1)

    console.log('Waiting for read model with id', mockCartId, 'to become available')
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
        const isExpected = result?.data?.ListCartReadModels?.items?.[0]?.checks >= 4
        return isDefined && isExpected
      }
    )
    console.log('Got result', JSON.stringify(queryResult))

    // After the command is executed, the register is flushed, so we will have the 4 cartItems
    expect(queryResult.data.ListCartReadModels?.items?.[0]?.checks).to.eq(4)
  })

  it('should create an event in the event store', async () => {
    console.log('Waiting for events to be available')
    const eventsCount = await waitForIt(
      () => applicationUnderTest.count.events(),
      (eventsCount: number) => eventsCount > 0
    )
    console.log('Events count', eventsCount)

    const mockCartId = random.uuid()
    console.log('Abandoning cart', mockCartId)
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
    console.log('Waiting for events to be available')
    await waitForIt(
      () => applicationUnderTest.count.events(),
      (newEventsCount: number) => newEventsCount > eventsCount
    )
    console.log('Events count', expectedEventItemsCount)

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
