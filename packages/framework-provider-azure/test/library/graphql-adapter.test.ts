/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { BoosterConfig, GraphQLRequestEnvelope } from '@boostercloud/framework-types'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
import { InvocationContext } from '@azure/functions'
import { AzureHttpFunctionInput } from '../../src'

/**
 * Creates a mock HttpRequest for v4 programming model.
 * In v4, HttpRequest is a class with methods like json(), text(), headers, etc.
 * @param body
 * @param headers
 */
function createMockHttpRequest(body: unknown, headers: Record<string, string>): any {
  const headersMap = new Map(Object.entries(headers))
  return {
    url: 'https://test.azurewebsites.net/api/graphql',
    method: 'POST',
    headers: {
      get: (key: string) => headers[key.toLowerCase()] || headers[key] || null,
      forEach: (callback: (value: string, key: string) => void) => {
        headersMap.forEach((value, key) => callback(value, key))
      },
    },
    json: async () => body,
    text: async () => JSON.stringify(body),
  }
}

/**
 * Creates a mock InvocationContext for v4 programming model.
 * @param invocationId - The invocation ID to set in the context.
 * @returns A mock InvocationContext object.
 */
function createMockInvocationContext(invocationId: string): InvocationContext {
  return {
    invocationId,
    functionName: 'graphql',
    extraInputs: { get: () => undefined },
    extraOutputs: {
      set: () => {},
    },
    log: () => {},
    trace: () => {},
    debug: () => {},
    info: () => {},
    warn: () => {},
    error: () => {},
    options: {} as any,
    triggerMetadata: {},
  } as unknown as InvocationContext
}

describe('GraphQL adapter', () => {
  describe('The "rawGraphQLRequestToEnvelope"', () => {
    it('Generates an envelope correctly from an Azure HTTP request (v4', async () => {
      const config = new BoosterConfig('test')
      config.logger = console
      const expectedQuery = 'GraphQL query'
      const expectedToken = 'token'
      const expectedVariables = {
        varOne: 3,
        varTwo: 'test',
      }
      const expectedBody = {
        query: expectedQuery,
        variables: expectedVariables,
      }

      const mockRequest = createMockHttpRequest(expectedBody, {
        authorization: expectedToken,
      })
      const mockContext = createMockInvocationContext('123')

      const input: AzureHttpFunctionInput = {
        request: mockRequest,
        context: mockContext,
      }

      const gotOutput = await rawGraphQLRequestToEnvelope(config, input)

      // Verify the envelope was created correctly
      const envelope = gotOutput as GraphQLRequestEnvelope
      expect(envelope).to.have.property('requestID', '123')
      expect(envelope).to.have.property('eventType', 'MESSAGE')
      expect(envelope).to.have.property('token', expectedToken)
      expect(envelope).to.have.deep.property('value', expectedBody)
      expect(envelope).to.have.property('context')
      expect(envelope.context?.request?.body).to.deep.equal(expectedBody)
      expect(envelope.context?.rawContext).to.equal(input)
    })
  })
})
