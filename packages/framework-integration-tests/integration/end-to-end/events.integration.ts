import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { graphQLClient, createUser, getUserAuthInformation } from '../providers/aws/utils'
import { internet } from 'faker'
import gql from 'graphql-tag'
import { expect } from 'chai'
import * as chai from 'chai'
import { createPassword } from '../helper/auth-helper'
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
            await expect(simpleQueryByType(anonymousClient, 'OrderCreated')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can not read events belonging to an entity authorized to some roles', async () => {
            await expect(simpleQueryByType(anonymousClient, 'CartItemChanged')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(simpleQueryByType(anonymousClient, 'ProductUpdated')).to.eventually.be.fulfilled
          })
        })

        context('with an authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(simpleQueryByType(loggedClient, 'OrderCreated')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can not read events belonging to an entity authorized to other role', async () => {
            await expect(simpleQueryByType(loggedClient, 'CartPaid')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(simpleQueryByType(loggedClient, 'ProductUpdated')).to.eventually.be.fulfilled
          })

          it('can read events belonging to an entity authorized for their role', async () => {
            await expect(simpleQueryByType(loggedClient, 'CartItemChanged')).to.eventually.be.fulfilled
          })
        })
      })

      context('when querying events by entity', () => {
        context('with a non-authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(simpleQueryByEntity(anonymousClient, 'Order')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can not read events belonging to an entity authorized to some roles', async () => {
            await expect(simpleQueryByEntity(anonymousClient, 'Cart')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(simpleQueryByEntity(anonymousClient, 'Product')).to.eventually.be.fulfilled
          })
        })

        context('with an authenticated user', () => {
          it('can not read events belonging to an entity with no authorization declaration', async () => {
            await expect(simpleQueryByEntity(loggedClient, 'Order')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can not read events belonging to an entity authorized to other role', async () => {
            await expect(simpleQueryByEntity(loggedClient, 'Payment')).to.eventually.be.rejectedWith(/Access denied for reading events/)
          })

          it('can read events belonging to an entity authorized for "all"', async () => {
            await expect(simpleQueryByEntity(loggedClient, 'Product')).to.eventually.be.fulfilled
          })

          it('can read events belonging to an entity authorized for their role', async () => {
            await expect(simpleQueryByEntity(loggedClient, 'Cart')).to.eventually.be.fulfilled
          })
        })
      })
    })
    context('when doing a query by entity', () => {
      context('with no time filters', () => {
        it('returns the expected events in the right order')
      })
      context('with time filters', () => {
        it('returns the expected events in the right order')
      })
    })
    context('when doing a query by entity and entityID', () => {})
    context('when doing a query by type', () => {})
  })
})

function simpleQueryByType(client: ApolloClient<unknown>, type: string): Promise<unknown> {
  return client.query({
    query: gql`
      query {
        eventsByType(type: ${type}) {
          value
        }
      }
    `,
  })
}

function simpleQueryByEntity(client: ApolloClient<unknown>, entity: string): Promise<unknown> {
  return client.query({
    query: gql`
      query {
        eventsByEntity(entity: ${entity}) {
          value
        }
      }
    `,
  })
}
