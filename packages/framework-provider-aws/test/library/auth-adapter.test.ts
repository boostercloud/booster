/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from 'chai'
import * as chai from 'chai'
import { rawSignUpDataToUserEnvelope } from '../../src/library/auth-adapter'
import { UserEnvelope } from '@boostercloud/framework-types'

chai.use(require('sinon-chai'))

describe('the auth-adapter', () => {
  describe('the `rawSignUpDataToUserEnvelope` returns user data from cognito event', () => {
    it('generates an envelope correctly from an AWS event', async () => {
      const cognitoUserEvent = {
        request: {
          userAttributes: {
            email: 'test@user.com',
            'custom:roles': 'Admin,User,Agent',
          },
        },
      }

      const expectedOuput: UserEnvelope = {
        email: cognitoUserEvent.request.userAttributes.email,
        roles: ['Admin', 'User', 'Agent'],
      }
      const gotOutput = rawSignUpDataToUserEnvelope(cognitoUserEvent as any)
      expect(gotOutput).to.be.deep.equal(expectedOuput)
    })
  })
})
