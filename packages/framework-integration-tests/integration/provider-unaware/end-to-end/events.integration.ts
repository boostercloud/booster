import { ApolloClient, ApolloQueryResult } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { internet, random } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { sleep, waitForIt } from '../../helper/sleep'
import {
  EventSearchResponse,
  EventTimeParameterFilter,
  PaginatedEntitiesIdsResult,
  UUID,
} from '@boostercloud/framework-types'
import { applicationUnderTest } from './setup'
import { unique } from '@boostercloud/framework-common-helpers'
chai.use(require('chai-as-promised'))

describe('Events end-to-end tests', () => {
  let anonymousClient: ApolloClient<NormalizedCacheObject>
  let loggedClient: ApolloClient<NormalizedCacheObject>
  let expiredClient: ApolloClient<NormalizedCacheObject>
  let beforeClient: ApolloClient<NormalizedCacheObject>
  let expiredAndBeforeClient: ApolloClient<NormalizedCacheObject>

  before(async () => {
    anonymousClient = applicationUnderTest.graphql.client()

    const userEmail = internet.email()
    const userToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail')
    loggedClient = applicationUnderTest.graphql.client(userToken)
    const expiredToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail', { expiresIn: 0 })
    expiredClient = applicationUnderTest.graphql.client(expiredToken)
    const notBefore = Math.floor(Date.now() / 1000) + 999999
    const beforeToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail', { notBefore })
    beforeClient = applicationUnderTest.graphql.client(beforeToken)
    const expiredAndBeforeToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail', {
      expiresIn: 0,
      notBefore,
    })
    expiredAndBeforeClient = applicationUnderTest.graphql.client(expiredAndBeforeToken)
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

        context('with an expired or not before token', () => {
          it('can not read events belonging to an entity with an expired token', async () => {
            await expect(queryByType(expiredClient, 'CartItemChanged'))
              .to.eventually.be.rejected.and.be.an.instanceOf(Error)
              .and.have.property('graphQLErrors')
              .and.have.to.be.deep.equal([
                {
                  message: 'TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired',
                  extensions: { code: 'BoosterTokenExpiredError' },
                },
              ])
          })

          it('can not read events belonging to an entity with a token not before', async () => {
            await expect(queryByType(beforeClient, 'CartItemChanged'))
              .to.eventually.be.rejected.and.be.an.instanceOf(Error)
              .and.have.property('graphQLErrors')
              .and.have.to.be.deep.equal([
                {
                  message: 'NotBeforeError: jwt not active\nNotBeforeError: jwt not active',
                  extensions: { code: 'BoosterTokenNotBeforeError' },
                },
              ])
          })

          // jwt.verify check NotBefore before Expired. If we have a token NotBefore and Expired we will get a BoosterTokenExpiredError error
          it('return BoosterTokenNotBeforeError with a token expired and not before', async () => {
            await expect(queryByType(expiredAndBeforeClient, 'CartItemChanged'))
              .to.eventually.be.rejected.and.be.an.instanceOf(Error)
              .and.have.property('graphQLErrors')
              .and.have.to.be.deep.equal([
                {
                  message: 'NotBeforeError: jwt not active\nNotBeforeError: jwt not active',
                  extensions: { code: 'BoosterTokenNotBeforeError' },
                },
              ])
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
              mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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

        context('with limit', () => {
          it('returns the expected events in the right order', async () => {
            const limit = 3
            const result = await queryByEntity(anonymousClient, 'Cart', undefined, mockCartId, limit)
            const events: Array<EventSearchResponse> = result.data['eventsByEntity']
            // As now the query included the entityId, we can be sure that ONLY the provisioned events were returned
            expect(events.length).to.be.equal(limit)
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

          it('returns the expected events in the right order if we include limit and time filters and the "to" is reached before the limit', async () => {
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
              mockCartId,
              numberOfProvisionedEvents * 2
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
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
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

  describe('Query events ids', () => {
    // warn: this is a non-deterministic test as it needs an empty list of anotherCounter as we can't filter the events search
    describe('without limit', () => {
      //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
      if (process.env.TESTED_PROVIDER === 'AWS') {
        console.log('****************** Warning **********************')
        console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS')
        console.log('*************************************************')
        return
      }

      let mockCounterId: string
      const mockCounterIds = [] as Array<UUID>
      let mockSameCounterId: string
      const numberOfProvisionedEvents = 3
      let mockIdentifier: string

      beforeEach(async () => {
        // Provision N events with same counterId
        mockSameCounterId = random.uuid()
        console.log(`Adding ${numberOfProvisionedEvents} events with id ${mockSameCounterId}`)
        for (let i = 0; i < numberOfProvisionedEvents; i++) {
          await anonymousClient.mutate({
            variables: {
              counterId: mockSameCounterId,
              identifier: mockSameCounterId,
            },
            mutation: gql`
              mutation IncrementCounter($counterId: ID!, $identifier: String!) {
                IncrementCounter(input: { counterId: $counterId, identifier: $identifier })
              }
            `,
          })
        }
        await waitForIt(
          () => {
            return anonymousClient.query({
              variables: {
                filterBy: { identifier: { eq: mockSameCounterId } },
              },
              query: gql`
                query ListCounterReadModels($filterBy: ListCounterReadModelFilter) {
                  ListCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const items = result?.data?.ListCounterReadModels?.items
            return items?.length > 0 && items[0].amount === numberOfProvisionedEvents
          }
        )

        // Provision N events with random counterId and same identifier
        mockIdentifier = random.uuid()
        console.log(`Adding ${numberOfProvisionedEvents} events with identifier ${mockIdentifier}`)
        for (let i = 0; i < numberOfProvisionedEvents; i++) {
          mockCounterId = random.uuid()
          mockCounterIds.push(mockCounterId)
          await anonymousClient.mutate({
            variables: {
              counterId: mockCounterId,
              identifier: mockIdentifier,
            },
            mutation: gql`
              mutation IncrementCounter($counterId: ID!, $identifier: String!) {
                IncrementCounter(input: { counterId: $counterId, identifier: $identifier })
              }
            `,
          })
        }
        await waitForIt(
          () => {
            return anonymousClient.query({
              variables: {
                filterBy: { identifier: { eq: mockIdentifier } },
              },
              query: gql`
                query ListCounterReadModels($filterBy: ListCounterReadModelFilter) {
                  ListCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListCounterReadModels?.items?.length === numberOfProvisionedEvents
        )
      })

      it('Should return all elements', async () => {
        const result = await anonymousClient.mutate({
          variables: {
            entityName: 'Counter',
            limit: 99999, // limit could not be mockSameCounterId as the test could be run several times
          },
          mutation: gql`
            mutation EntitiesIdsFinder($entityName: String!, $limit: Float!) {
              EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit })
            }
          `,
        })

        const events: PaginatedEntitiesIdsResult = result?.data?.EntitiesIdsFinder

        // counter with same id should be only 1
        const sameCounterIdEvents = events.items.filter((event) => event.entityID === mockSameCounterId)
        expect(sameCounterIdEvents.length).to.be.equal(1)

        // all random counters should be returned
        const currentEntitiesIds = events.items.map((item) => item.entityID)
        expect(currentEntitiesIds).to.include.members(mockCounterIds)
        const distinctCurrentEntitiesIds = unique(events.items.map((item) => item.entityID))
        expect(distinctCurrentEntitiesIds.length).to.be.equal(currentEntitiesIds.length)

        // There are exactly the expected number of ids
        expect(currentEntitiesIds.length).to.be.equal(numberOfProvisionedEvents + 1)
      })
    })

    // warn: this is a non-deterministic test as it needs an empty list of anotherCounter as we can't filter the events search
    describe('paginated with limit 1', () => {
      //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
      if (process.env.TESTED_PROVIDER === 'AWS') {
        console.log('****************** Warning **********************')
        console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS')
        console.log('*************************************************')
        return
      }

      let mockAnotherCounterId: string
      const mockAnotherCounterIds = [] as Array<UUID>
      let mockSameAnotherCounterId: string
      const numberOfProvisionedEvents = 3
      let mockIdentifier: string

      beforeEach(async () => {
        // Provision N events with same anotherCounterId
        mockSameAnotherCounterId = random.uuid()
        console.log(`Adding ${numberOfProvisionedEvents} events with id ${mockSameAnotherCounterId}`)
        for (let i = 0; i < numberOfProvisionedEvents; i++) {
          await anonymousClient.mutate({
            variables: {
              anotherCounterId: mockSameAnotherCounterId,
              identifier: mockSameAnotherCounterId,
            },
            mutation: gql`
              mutation IncrementAnotherCounter($anotherCounterId: ID!, $identifier: String!) {
                IncrementAnotherCounter(input: { anotherCounterId: $anotherCounterId, identifier: $identifier })
              }
            `,
          })
        }
        await waitForIt(
          () => {
            return anonymousClient.query({
              variables: {
                filterBy: { identifier: { eq: mockSameAnotherCounterId } },
              },
              query: gql`
                query ListAnotherCounterReadModels($filterBy: ListAnotherCounterReadModelFilter) {
                  ListAnotherCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
            })
          },
          (result) => {
            const items = result?.data?.ListAnotherCounterReadModels?.items
            return items?.length > 0 && items[0].amount === numberOfProvisionedEvents
          }
        )

        // Provision N events with random anotherCounterId and same identifier
        mockIdentifier = random.uuid()
        console.log(`Adding ${numberOfProvisionedEvents} events with identifier ${mockIdentifier}`)
        for (let i = 0; i < numberOfProvisionedEvents; i++) {
          mockAnotherCounterId = random.uuid()
          mockAnotherCounterIds.push(mockAnotherCounterId)
          await anonymousClient.mutate({
            variables: {
              anotherCounterId: mockAnotherCounterId,
              identifier: mockIdentifier,
            },
            mutation: gql`
              mutation IncrementAnotherCounter($anotherCounterId: ID!, $identifier: String!) {
                IncrementAnotherCounter(input: { anotherCounterId: $anotherCounterId, identifier: $identifier })
              }
            `,
          })
        }
        await waitForIt(
          () => {
            return anonymousClient.query({
              variables: {
                filterBy: { identifier: { eq: mockIdentifier } },
              },
              query: gql`
                query ListAnotherCounterReadModels($filterBy: ListAnotherCounterReadModelFilter) {
                  ListAnotherCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
            })
          },
          (result) => result?.data?.ListAnotherCounterReadModels?.items?.length === numberOfProvisionedEvents
        )
      })

      it('Should return the exact number of pages', async () => {
        let cursor: Record<'id', string> | undefined = undefined
        let count = 9999
        let pages = 0
        const items = []
        while (count != 0) {
          const result: any = await anonymousClient.mutate({
            variables: {
              entityName: 'AnotherCounter',
              limit: 1,
              afterCursor: cursor,
            },
            mutation: gql`
              mutation EntitiesIdsFinder($entityName: String!, $limit: Float!, $afterCursor: JSONObject) {
                EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit, afterCursor: $afterCursor })
              }
            `,
          })

          cursor = result.data.EntitiesIdsFinder.cursor
          count = result.data.EntitiesIdsFinder.count
          if (count !== 0) {
            pages++
            items.push(...result.data.EntitiesIdsFinder?.items)
            console.log(`Pages ${pages}`)
          }
        }
        expect(pages).to.be.eq(numberOfProvisionedEvents + 1)

        // counter with same id should be only 1
        const sameCounterIdEvents = items.filter((event) => event.entityID == mockSameAnotherCounterId)
        expect(sameCounterIdEvents.length).to.be.equal(1)

        // all random counters should be returned
        const currentEntitiesIds = items.map((item) => item.entityID)
        expect(currentEntitiesIds).to.include.members(mockAnotherCounterIds)
        const distinctCurrentEntitiesIds = unique(items.map((item) => item.entityID))
        expect(distinctCurrentEntitiesIds.length).to.be.equal(currentEntitiesIds.length)

        // There are exactly the expected number of ids
        expect(currentEntitiesIds.length).to.be.equal(numberOfProvisionedEvents + 1)
      })
    })
  })
})

function queryByType(
  client: ApolloClient<unknown>,
  type: string,
  timeFilters?: EventTimeParameterFilter,
  limit?: number
): Promise<ApolloQueryResult<any>> {
  const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : ''
  const queryLimit = limit ? `, limit:${limit}` : ''
  return client.query({
    query: gql`
      query {
        eventsByType(type: ${type}${queryTimeFilters}${queryLimit}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                roles
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
  timeFilters?: EventTimeParameterFilter,
  entityID?: string,
  limit?: number
): Promise<ApolloQueryResult<any>> {
  const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : ''
  const queryEntityID = entityID ? `, entityID:"${entityID}"` : ''
  const queryLimit = limit ? `, limit:${limit}` : ''
  return client.query({
    query: gql`
      query {
        eventsByEntity(entity: ${entity}${queryEntityID}${queryTimeFilters}${queryLimit}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                roles
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
    expect(value.cartId).not.to.be.undefined
  }
}
