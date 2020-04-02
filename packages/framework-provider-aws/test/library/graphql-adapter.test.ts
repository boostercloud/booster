/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from 'chai'
import * as chai from 'chai'
import { GraphQLRequestEnvelope, UserEnvelope } from '@boostercloud/framework-types'
import { APIGatewayProxyWithLambdaAuthorizerEvent } from 'aws-lambda'
import { AuthorizerWithUserData } from '../../src/library/auth-adapter'
import { rawGraphQLRequestToEnvelope } from '../../src/library/graphql-adapter'
chai.use(require('sinon-chai'))

describe('the graphql-adapter', () => {
  describe('the `rawGraphQLRequestToEnvelope`', () => {
    it('generates an envelope correctly from an AWS event', async () => {
      const expectedUser: UserEnvelope = {
        email: 'test@user.com',
        roles: ['Admin'],
      }
      const expectedQuery = 'GraphQL query'
      const request: APIGatewayProxyWithLambdaAuthorizerEvent<AuthorizerWithUserData> = {
        requestContext: {
          requestId: '123',
          eventType: 'CONNECT',
          connectionId: '456',
          authorizer: {
            userJSON: JSON.stringify(expectedUser),
          },
        },
        body: JSON.stringify({
          query: expectedQuery,
        }),
      } as any

      const expectedOutput: GraphQLRequestEnvelope = {
        requestID: '123',
        eventType: 'CONNECT',
        connectionID: '456',
        currentUser: expectedUser,
        value: expectedQuery,
      }
      const gotOutput = await rawGraphQLRequestToEnvelope(request, console)

      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })
  })
})
