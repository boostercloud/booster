import { ApolloClient, ApolloQueryResult } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { getTokenForUser, graphQLClient } from '../providers/aws/utils'
import { internet, random } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { sleep, waitForIt } from '../helper/sleep'
import { EventSearchResponse, EventTimeFilter } from '@boostercloud/framework-types'
chai.use(require('chai-as-promised'))

describe('Events end-to-end tests', () => {
  let anonymousClient: ApolloClient<NormalizedCacheObject>
  let loggedClient: ApolloClient<NormalizedCacheObject>

  before(async () => {
    anonymousClient = await graphQLClient()

    const userEmail = internet.email()
    // TODO: Make retrieval of auth token cloud agnostic
    const userToken = await getTokenForUser(userEmail, 'UserWithEmail')
    loggedClient = await graphQLClient(userToken)
  })

  describe('Query events', () => {
    describe('the authorization mechanism', () => {
      context('when querying events by type', () => {
        context('with a non-authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(queryByType(anonymousClient, 'OrderCreated')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can not read events belonging to an entity authorized to some roles', async () => {
            await expect(queryByType(anonymousClient, 'ProductUpdated')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(queryByType(anonymousClient, 'CartItemChanged')).to.eventually.be.fulfilled
          })
        })

        context('with an authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(queryByType(loggedClient, 'OrderCreated')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can not read events belonging to an entity authorized to other role', async () => {
            await expect(queryByType(loggedClient, 'CartPaid')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(queryByType(loggedClient, 'CartItemChanged')).to.eventually.be.fulfilled
          })

          it('can read events belonging to an entity authorized for their role', async () => {
            await expect(queryByType(loggedClient, 'ProductUpdated')).to.eventually.be.fulfilled
          })
        })
      })

      context('when querying events by entity', () => {
        context('with a non-authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(queryByEntity(anonymousClient, 'Order')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can not read events belonging to an entity authorized to some roles', async () => {
            await expect(queryByEntity(anonymousClient, 'Product')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(queryByEntity(anonymousClient, 'Cart')).to.eventually.be.fulfilled
          })
        })

        context('with an authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(queryByEntity(loggedClient, 'Order')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can not read events belonging to an entity authorized to other role', async () => {
            await expect(queryByEntity(loggedClient, 'Payment')).to.eventually.be.rejectedWith(
              /Access denied for reading events/
            )
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(queryByEntity(loggedClient, 'Cart')).to.eventually.be.fulfilled
          })

          it('can read events belonging to an entity authorized for their role', async () => {
            await expect(queryByEntity(loggedClient, 'Product')).to.eventually.be.fulfilled
          })
        })
      })
    })
    describe('the result of the queries', () => {
      let mockCartId: string
      let mockProductId: string
      let mockQuantity: number
      const numberOfProvisionedEvents = 10
      let eventsProvisionedStartedAt: Date
      let eventsProvisionedFinishedAt: Date

      beforeEach(async () => {
        mockCartId = random.uuid()
        mockProductId = random.uuid()
        mockQuantity = random.number({ min: 1 })
        // Provision 10 events leaving like 100ms of time between them to have a clear time order.
        eventsProvisionedStartedAt = new Date()
        for (let i = 0; i < numberOfProvisionedEvents; i++) {
          await anonymousClient.mutate({
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
          await sleep(100)
        }
        eventsProvisionedFinishedAt = new Date()
      })

      context('when doing a query by entity', () => {
        context('with no time filters', () => {
          it('returns the expected events in the right order', async () => {
            const result = await queryByEntity(anonymousClient, 'Cart')
            // As we are querying by just Entity, we will get the events we provisioned plus others (possible MANY others)
            // with different IDs. The only things we can check are:
            // - That, at least, we have "numberOfProvisionedEvents" events
            // - The structure is the expected one
            // - They came sorted by "createdAt" in descending order.
            const events: Array<EventSearchResponse> = result.data['eventsByEntity'].slice(0, numberOfProvisionedEvents)
            expect(events.length).to.be.equal(numberOfProvisionedEvents)
            checkOrderAndStructureOfEvents(events)
          })
        })

        context('with time filters', () => {
          it('returns the expected events in the right order', async () => {
            // Fetch events with a time filter that goes from 1 second before the provisioning started to 1 seconds
            // after the provisioning finished. This way we will get, at least, all the events that were provisioned,
            // and that's that we will check
            //
            // The "one second before and after" is to leave enough room for possible misalignment between the
            // clocks (remember we are using the ISO format, so we can forget about timezones and all that complicated stuff)
            const from = new Date(eventsProvisionedStartedAt)
            from.setSeconds(from.getSeconds() - 1)
            const to = new Date(eventsProvisionedFinishedAt)
            to.setSeconds(to.getSeconds() + 1)

            const result = await queryByEntity(anonymousClient, 'Cart', {
              from: from.toISOString(),
              to: to.toISOString(),
            })
            const events: Array<EventSearchResponse> = result.data['eventsByEntity']
            // First check the order and structure
            checkOrderAndStructureOfEvents(events)
            // Now check if the time filter worked well
            let provisionedEventsFound = 0
            for (const event of events) {
              if (event.entityID === mockCartId) {
                provisionedEventsFound++
              }
            }
            expect(provisionedEventsFound).to.be.gte(numberOfProvisionedEvents)
          })
        })
      })

      context('when doing a query by entity and entityID', () => {
        context('with no time filters', () => {
          it('returns the expected events in the right order', async () => {
            const result = await queryByEntity(anonymousClient, 'Cart', undefined, mockCartId)
            const events: Array<EventSearchResponse> = result.data['eventsByEntity']
            // As now the query included the entityId, we can be sure that ONLY the provisioned events were returned
            expect(events.length).to.be.equal(numberOfProvisionedEvents)
            checkOrderAndStructureOfEvents(events)
            for (const event of events) {
              expect(event.type).to.be.equal('CartItemChanged')
              expect(event.entityID).to.be.equal(mockCartId)
              const value: Record<string, string> = event.value as any
              expect(value.productId).to.be.equal(mockProductId)
              expect(value.quantity).to.be.equal(mockQuantity)
            }
          })
        })

        context('with time filters', () => {
          it('returns the expected events in the right order', async () => {
            // Let's use a time filter that tries to get half of the events we provisioned. We can't be sure we will get
            // exactly half of them, because possible clock differences, but we will check using inequalities
            const from = new Date(eventsProvisionedStartedAt)
            from.setSeconds(from.getSeconds() - 1)
            const halfTheDuration = (eventsProvisionedFinishedAt.getTime() - eventsProvisionedStartedAt.getTime()) / 2
            const to = new Date(eventsProvisionedStartedAt.getTime() + halfTheDuration)

            const result = await queryByEntity(
              anonymousClient,
              'Cart',
              {
                from: from.toISOString(),
                to: to.toISOString(),
              },
              mockCartId
            )
            const events: Array<EventSearchResponse> = result.data['eventsByEntity']
            // First check the order and structure
            checkOrderAndStructureOfEvents(events)
            // Now we check that we have received more than 0 events and less than number we provisioned, as time filters
            // we used should have given us less events than what we provisioned
            expect(events.length).to.be.within(1, numberOfProvisionedEvents - 1)
          })
        })
      })

      context('when doing a query by type', () => {
        context('with no time filters', () => {
          it('returns the expected events in the right order', async () => {
            const result = await queryByType(anonymousClient, 'CartItemChanged')
            // As we are querying by just event type, we will get the events we provisioned plus others (possible MANY others)
            // with different IDs. The only things we can check are:
            // - That, at least, we have "numberOfProvisionedEvents" events
            // - The structure is the expected one
            // - They came sorted by "createdAt" in descenting order.
            const events: Array<EventSearchResponse> = result.data['eventsByType'].slice(0, numberOfProvisionedEvents)
            expect(events.length).to.be.equal(numberOfProvisionedEvents)
            checkOrderAndStructureOfEvents(events)
          })
        })

        context('with time filters', () => {
          it('returns the expected events in the right order', async () => {
            // The structure and the reasons of why this test is this way are exactly the same as described in tests:
            // 'when doing a query by type'.'with time filters'.'returns the expected events in the right order'
            const from = new Date(eventsProvisionedStartedAt)
            from.setSeconds(from.getSeconds() - 1)
            const to = new Date(eventsProvisionedFinishedAt)
            to.setSeconds(to.getSeconds() + 1)

            const result = await queryByType(anonymousClient, 'CartItemChanged', {
              from: from.toISOString(),
              to: to.toISOString(),
            })
            const events: Array<EventSearchResponse> = result.data['eventsByType']
            // First check the order and structure
            checkOrderAndStructureOfEvents(events)
            // Now check if the time filter worked well
            let provisionedEventsFound = 0
            for (const event of events) {
              if (event.entityID === mockCartId) {
                provisionedEventsFound++
              }
            }
            expect(provisionedEventsFound).to.be.gte(numberOfProvisionedEvents)
          })
        })
      })
    })
    describe('the result of the queries involving many events', () => {
      let mockCartId: string
      const numberOfProvisionedEvents = 150

      beforeEach(async () => {
        const mutationPromises: Array<Promise<unknown>> = []
        mockCartId = random.uuid()
        for (let i = 0; i < numberOfProvisionedEvents; i++) {
          mutationPromises.push(
            anonymousClient.mutate({
              variables: {
                cartId: mockCartId,
                productId: random.uuid(),
                quantity: 1,
              },
              mutation: gql`
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float) {
                  ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
                }
              `,
            })
          )
        }
        await Promise.all(mutationPromises)
      })
      context('when doing a query that would return many (150) events', () => {
        it('returns the expected result', async () => {
          console.log(mockCartId)
          const result = await waitForIt(
            () => queryByEntity(anonymousClient, 'Cart', undefined, mockCartId),
            (result) => {
              return result.data['eventsByEntity'].length === numberOfProvisionedEvents
            }
          )
          const events: Array<EventSearchResponse> = result.data['eventsByEntity']
          expect(events.length).to.be.equal(numberOfProvisionedEvents)
          checkOrderAndStructureOfEvents(events)
          for (const event of events) {
            expect(event.type).to.be.equal('CartItemChanged')
            expect(event.entityID).to.be.equal(mockCartId)
          }
        })
      })
    })
  })
})

function queryByType(
  client: ApolloClient<unknown>,
  type: string,
  timeFilters?: EventTimeFilter
): Promise<ApolloQueryResult<any>> {
  const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : ''
  return client.query({
    query: gql`
      query {
        eventsByType(type: ${type}${queryTimeFilters}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                role
                username
            }
            value
        }
      }
    `,
  })
}

function queryByEntity(
  client: ApolloClient<unknown>,
  entity: string,
  timeFilters?: EventTimeFilter,
  entityID?: string
): Promise<ApolloQueryResult<any>> {
  const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : ''
  const queryEntityID = entityID ? `, entityID:"${entityID}"` : ''
  return client.query({
    query: gql`
      query {
        eventsByEntity(entity: ${entity}${queryEntityID}${queryTimeFilters}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                role
                username
            }
            value
        }
      }
    `,
  })
}

function checkOrderAndStructureOfEvents(events: Array<EventSearchResponse>): void {
  // First check if they are in the right order (from more recent to older)
  const eventsSorted = [...events].sort((a, b) => {
    if (a.createdAt > b.createdAt) return -1
    if (a.createdAt < b.createdAt) return 1
    return 0
  })
  expect(eventsSorted).to.be.deep.equal(events)
  // Now check if the structure and some of their fields are correct
  for (const event of events) {
    expect(event).to.have.keys('__typename', 'createdAt', 'entity', 'entityID', 'requestID', 'type', 'user', 'value')
    expect(event.entity).to.be.equal('Cart')
    // In this function, we can't check for specific values of type, entityID or value.productID because other tests
    // (and ScheduledCommands) could have created other events for the Cart entity with different values for those properties.
    // What we can check is only its presence:
    expect(event.type).not.to.be.undefined
    expect(event.entityID).not.to.be.undefined
    const value: Record<string, string> = event.value as any
    expect(value.productId).not.to.be.undefined
  }
}
