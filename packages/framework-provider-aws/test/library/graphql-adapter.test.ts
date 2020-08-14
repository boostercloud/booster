/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { GraphQLRequestEnvelope, GraphQLRequestEnvelopeError, UserEnvelope } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { SinonStub, stub, restore } from 'sinon'
import * as authAdapter from '../../src/library/auth-adapter'
import { internet, random } from 'faker'
import { APIGatewayProxyEvent } from 'aws-lambda'

describe('AWS Provider graphql-adapter', () => {
  let userPoolStub: SinonStub
  let userEnvelopeFromAuthToken: SinonStub

  beforeEach(() => {
    userPoolStub = stub()
    userEnvelopeFromAuthToken = stub(authAdapter, 'userEnvelopeFromAuthToken')
  })

  afterEach(() => {
    restore()
  })

  describe('the `rawGraphQLRequestToEnvelope`', () => {
    let mockRequestId: string
    let mockConnectionId: string
    let mockToken: string

    let expectedUser: UserEnvelope
    let expectedQuery: string
    let expectedVariables: object
    let request: APIGatewayProxyEvent

    beforeEach(() => {
      mockRequestId = random.number().toString()
      mockConnectionId = random.uuid()
      mockToken = random.uuid()

      expectedUser = {
        username: internet.email(),
        role: 'Admin',
      }
      expectedQuery = 'GraphQL query'
      expectedVariables = {
        varOne: random.number(),
        varTwo: random.alphaNumeric(10),
      }
      request = {
        headers: {
          Authorization: mockToken,
        },
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

    it('should call userEnvelopeFromAuthToken with expected arguments', async () => {
      await rawGraphQLRequestToEnvelope(userPoolStub as any, request, console)
      expect(userEnvelopeFromAuthToken).to.have.been.calledOnceWithExactly(userPoolStub, mockToken)
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

      userEnvelopeFromAuthToken.resolves(expectedUser)

      const gotOutput = await rawGraphQLRequestToEnvelope(userPoolStub as any, request, console)

      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })

    context('when there is an error retrieving the user', () => {
      const mockError = new Error('fake error')
      beforeEach(() => {
        userEnvelopeFromAuthToken.throws(mockError)
      })

      it('generates an envelope with the error', async () => {
        const expectedOutput: GraphQLRequestEnvelopeError = {
          requestID: mockRequestId,
          eventType: 'CONNECT',
          connectionID: mockConnectionId,
          error: mockError,
        }

        const gotOutput = await rawGraphQLRequestToEnvelope(userPoolStub as any, request, console)

        expect(gotOutput).to.be.deep.equal(expectedOutput)
      })
    })
  })
})
