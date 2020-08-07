/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { Context } from '@azure/functions'

describe('GraphQL adapter', () => {
  describe('The "rawGraphQLRequestToEnvelope"', () => {
    it('Generates an envelope correctly from an Azure event', async () => {
      const expectedQuery = 'GraphQL query'
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
        },
        executionContext: {
          invocationId: '123',
        },
      } as any

      const expectedOutput: GraphQLRequestEnvelope = {
        requestID: '123',
        eventType: 'MESSAGE',
        value: {
          query: expectedQuery,
          variables: expectedVariables,
        },
      }
      const gotOutput = await rawGraphQLRequestToEnvelope(request, console)

      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })
  })
})
