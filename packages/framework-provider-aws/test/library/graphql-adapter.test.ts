/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { restore } from 'sinon'
import { random } from 'faker'
import { APIGatewayProxyEvent } from 'aws-lambda'
import { BoosterConfig } from '@boostercloud/framework-types'

describe('AWS Provider graphql-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawGraphQLRequestToEnvelope`', () => {
    let mockRequestId: string
    let mockConnectionId: string
    let mockToken: string

    let expectedQuery: string
    let expectedVariables: object
    let request: APIGatewayProxyEvent
    let expectedOutput: GraphQLRequestEnvelope

    beforeEach(() => {
      mockRequestId = random.number().toString()
      mockConnectionId = random.uuid()
      mockToken = random.uuid()
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

      expectedOutput = {
        requestID: mockRequestId,
        eventType: 'CONNECT',
        connectionID: mockConnectionId,
        token: mockToken,
        value: {
          query: expectedQuery,
          variables: expectedVariables,
        },
        context: {
          request: {
            headers: {
              Authorization: mockToken,
            },
            body: JSON.stringify({
              query: expectedQuery,
              variables: expectedVariables,
            }),
          },
          rawContext: request,
        },
      }
    })

    it('generates an envelope correctly from an AWS event', async () => {
      const config = new BoosterConfig('test')
      const gotOutput = await rawGraphQLRequestToEnvelope(config, request)
      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })
  })
})
