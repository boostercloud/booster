/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { rawSignUpDataToUserEnvelope } from '../../src/library/auth-adapter'
import { UserEnvelope } from '@boostercloud/framework-types'
import { restore } from 'sinon'

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
            'custom:role': 'User',
          },
        },
      }

      const expectedOutput: UserEnvelope = {
        username: cognitoUserEvent.request.userAttributes.email,
        role: 'User',
      }
      const gotOutput = rawSignUpDataToUserEnvelope(cognitoUserEvent as any)
      expect(gotOutput).to.be.deep.equal(expectedOutput)
    })
  })
})
