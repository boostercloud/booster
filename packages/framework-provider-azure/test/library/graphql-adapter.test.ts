/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { BoosterConfig, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { Context } from '@azure/functions'

describe('GraphQL adapter', () => {
  describe('The "rawGraphQLRequestToEnvelope"', () => {
    it('Generates an envelope correctly from an Azure event', async () => {
      const config = new BoosterConfig('test')
      config.logger = console
      const expectedQuery = 'GraphQL query'
      const expectedToken = 'token'
      const expectedVariables = {
        varOne: 3,
        varTwo: 'test',
      }
      const request: Context = {
        req: {
          body: {
            query: expectedQuery,
            variables: expectedVariables,
          },
          headers: {
            authorization: expectedToken,
          },
        },
        executionContext: {
          invocationId: '123',
        },
      } as any

      const expectedOutput: GraphQLRequestEnvelope = {
        requestID: '123',
        eventType: 'MESSAGE',
        token: expectedToken,
        value: {
          query: expectedQuery,
          variables: expectedVariables,
        },
        context: {
          request: {
            body: {
              query: expectedQuery,
              variables: expectedVariables,
            },
            headers: {
              authorization: expectedToken,
            },
          },
          rawContext: request,
        },
      }
      const gotOutput = await rawGraphQLRequestToEnvelope(config, request)
      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })
  })
})
