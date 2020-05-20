/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { authorizeRequest, AuthorizerWithUserData, rawSignUpDataToUserEnvelope } from '../../src/library/auth-adapter'
import { UserEnvelope } from '@boostercloud/framework-types'
import { APIGatewayAuthorizerWithContextResult } from 'aws-lambda'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { replace, restore } from 'sinon'
import * as UserEnvelopes from '../../src/library/user-envelopes'

describe('the auth-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawSignUpDataToUserEnvelope`', () => {
    it('generates an envelope correctly from an AWS event', async () => {
      const cognitoUserEvent = {
        request: {
          userAttributes: {
            email: 'test@user.com',
            'custom:roles': 'Admin,User,Agent',
          },
        },
      }

      const expectedOutput: UserEnvelope = {
        email: cognitoUserEvent.request.userAttributes.email,
        roles: ['Admin', 'User', 'Agent'],
      }
      const gotOutput = rawSignUpDataToUserEnvelope(cognitoUserEvent as any)
      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })
  })

  describe('the `authorizeRequest`', () => {
    it('returns a proper authorization schema for anonymous requests', async () => {
      const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
      const request = {
        methodArn: 'arn',
      }

      const expectedOutput: APIGatewayAuthorizerWithContextResult<AuthorizerWithUserData> = {
        principalId: 'anonymous',
        policyDocument: {
          Version: '2012-10-17',
          Statement: [
            {
              Action: 'execute-api:Invoke',
              Effect: 'Allow',
              Resource: request.methodArn,
            },
          ],
        },
        context: {
          userJSON: 'null',
        },
      }

      const gotOutput = await authorizeRequest(userPool, request as any, console)
      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })

    it('returns a proper authorization schema for authenticated requests', async () => {
      const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
      const expectedUser: UserEnvelope = {
        email: 'test@user.com',
        roles: ['Admin'],
      }
      replace(UserEnvelopes, 'fetchUserFromRequest', () => {
        return Promise.resolve(expectedUser)
      })
      const request = {
        methodArn: 'arn',
      }

      const expectedOutputWithoutContext: APIGatewayAuthorizerWithContextResult<AuthorizerWithUserData> = {
        principalId: expectedUser.email,
        policyDocument: {
          Version: '2012-10-17',
          Statement: [
            {
              Action: 'execute-api:Invoke',
              Effect: 'Allow',
              Resource: request.methodArn,
            },
          ],
        },
      } as any

      const gotOutput = await authorizeRequest(userPool, request as any, console)
      // First check the context
      const gotUserInContext = JSON.parse(gotOutput.context.userJSON)
      expect(gotUserInContext).to.be.deep.equal(expectedUser)

      // Now delete the context and check the rest of the properties. We do this to avoid comparing JSON
      // serializations directly, as the order in the properties might change and make the test fail when,
      // indeed, the result is right.
      delete gotOutput['context']
      expect(gotOutput).to.be.deep.equal(expectedOutputWithoutContext)
    })
  })
})
