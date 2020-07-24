/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { GraphQLRequestEnvelope, UserEnvelope } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { SinonStub, stub, restore } from 'sinon'
import * as userEnvelopes from '../../src/library/user-envelopes'
import { internet, random } from 'faker'
import { APIGatewayProxyEvent } from 'aws-lambda'

describe('AWS Provider graphql-adapter', () => {
  let userPoolStub: SinonStub
  let fetchUserFromRequestStub: SinonStub

  beforeEach(() => {
    userPoolStub = stub()
    fetchUserFromRequestStub = stub(userEnvelopes, 'fetchUserFromRequest')
  })

  afterEach(() => {
    restore()
  })

  describe('the `rawGraphQLRequestToEnvelope`', () => {
    let mockRequestId: string
    let mockConnectionId: string

    let expectedUser: UserEnvelope
    let expectedQuery: string
    let expectedVariables: object
    let request: APIGatewayProxyEvent

    beforeEach(() => {
      mockRequestId = random.number().toString()
      mockConnectionId = random.uuid()

      expectedUser = {
        email: internet.email(),
        roles: ['Admin'],
      }
      expectedQuery = 'GraphQL query'
      expectedVariables = {
        varOne: random.number(),
        varTwo: random.alphaNumeric(10),
      }
      request = {
        requestContext: {
          requestId: mockRequestId,
          eventType: 'CONNECT',
          connectionId: mockConnectionId,
        },
        body: JSON.stringify({
          query: expectedQuery,
          variables: expectedVariables,
        }),
      } as any
    })

    it('should call fetchUserFromRequest with expected arguments', async () => {
      await rawGraphQLRequestToEnvelope(userPoolStub as any, request, console)

      expect(fetchUserFromRequestStub).to.have.been.calledOnceWithExactly(userPoolStub, request, undefined)
    })

    it('generates an envelope correctly from an AWS event', async () => {
      const expectedOutput: GraphQLRequestEnvelope = {
        requestID: mockRequestId,
        eventType: 'CONNECT',
        connectionID: mockConnectionId,
        currentUser: expectedUser,
        value: {
          query: expectedQuery,
          variables: expectedVariables,
        },
      }

      fetchUserFromRequestStub.resolves(expectedUser)

      const gotOutput = await rawGraphQLRequestToEnvelope(userPoolStub as any, request, console)

      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })

    describe('Authorization as part of requests body', () => {
      let mockAuthorizationToken: string

      beforeEach(() => {
        mockAuthorizationToken = random.uuid()
        request = {
          requestContext: {
            requestId: mockRequestId,
            eventType: 'CONNECT',
            connectionId: mockConnectionId,
          },
          body: JSON.stringify({
            query: expectedQuery,
            variables: expectedVariables,
            payload: {
              Authorization: mockAuthorizationToken,
            },
          }),
        } as any
      })

      it('should call fetchUserFromRequest with expected arguments', async () => {
        await rawGraphQLRequestToEnvelope(userPoolStub as any, request, console)

        expect(fetchUserFromRequestStub).to.have.been.calledOnceWithExactly(
          userPoolStub,
          request,
          mockAuthorizationToken
        )
      })
    })
  })
})
