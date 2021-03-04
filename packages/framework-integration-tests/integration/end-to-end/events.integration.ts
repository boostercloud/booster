import { ApolloClient, ApolloQueryResult } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient, createUser, getUserAuthInformation } from '../providers/aws/utils'
import { internet, random } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { createPassword } from '../helper/auth-helper'
import { sleep } from '../helper/sleep'
import { EventSearchResponse, EventTimeFilter } from '@boostercloud/framework-types'
chai.use(require('chai-as-promised'))

describe('Events end-to-end tests', () => {
  let anonymousClient: ApolloClient<NormalizedCacheObject>
  let loggedClient: ApolloClient<NormalizedCacheObject>

  before(async () => {
    anonymousClient = await graphQLClient()

    const userEmail = internet.email()
    const userPassword = createPassword()
    // TODO: Make retrieval of auth token cloud agnostic
    await createUser(userEmail, userPassword, 'UserWithEmail')
    const userAuthInformation = await getUserAuthInformation(userEmail, userPassword)
    loggedClient = await graphQLClient(userAuthInformation.idToken)
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
            const lastTenEvents: Array<EventSearchResponse> = result.data['eventsByEntity'].slice(0, 10)
            expect(lastTenEvents.length).to.be.equal(10)
            checkOrderAndStructureOfEvents(lastTenEvents)
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
            const foundEvents: Array<EventSearchResponse> = result.data['eventsByEntity']
            // First check the order and structure
            checkOrderAndStructureOfEvents(foundEvents)
            // Now check if the time filter worked well
            let provisionedEventsFound = 0
            for (const foundEvent of foundEvents) {
              if (foundEvent.entityID === mockCartId) {
                provisionedEventsFound++
              }
            }
            expect(provisionedEventsFound).to.be.gte(numberOfProvisionedEvents)
          })
        })
      })
      context('when doing a query by entity and entityID', () => {})
      context('when doing a query by type', () => {})

      function checkOrderAndStructureOfEvents(events: Array<EventSearchResponse>): void {
        // First check if they are in the right order (from more recent to older)
        const lastTenEventsSorted = [...events].sort((a, b) => {
          if (a.createdAt > b.createdAt) return -1
          if (a.createdAt < b.createdAt) return 1
          return 0
        })
        expect(lastTenEventsSorted).to.be.deep.equal(events)
        // Now check if the structure and some of their fields are correct
        for (const event of events) {
          expect(event).to.have.keys(
            '__typename',
            'createdAt',
            'entity',
            'entityID',
            'requestID',
            'type',
            'user',
            'value'
          )
          expect(event.entity).to.be.equal('Cart')
          expect(event.type).to.be.equal('CartItemChanged')
          // We can't check for specific values of type, entityID or value.productID because other tests (and ScheduledCommands)
          // could have created other events for the Cart entity with different values for those properties.
          // What we can check is only its presence:
          expect(event.type).not.to.be.undefined
          expect(event.entityID).not.to.be.undefined
          const value: Record<string, string> = event.value as any
          expect(value.productId).not.to.be.undefined
        }
      }
    })
    describe('the result of involving many events', () => {
      context('when doing a query that would return 200 events', () => {})
    })
  })
})

function queryByType(client: ApolloClient<unknown>, type: string, timeFilters?: EventTimeFilter): Promise<unknown> {
  return client.query({
    query: gql`
      query {
        eventsByType(type: ${type}) {
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
  timeFilters?: EventTimeFilter
): Promise<ApolloQueryResult<any>> {
  const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : ''
  return client.query({
    query: gql`
      query {
        eventsByEntity(entity: ${entity}${queryTimeFilters}) {
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
