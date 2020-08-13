/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { rawSignUpDataToUserEnvelope, enrichRawMessage } from '../../src/library/auth-adapter'
import { UserEnvelope } from '@boostercloud/framework-types'
import { restore } from 'sinon'
import { BoosterConfig } from '@boostercloud/framework-types'

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

  describe('the `enrichRawMessage`', () => {
    const config = new BoosterConfig('test')

    const cognitoUserEventEmail = {
      request: {
        userAttributes: {
          email: 'test@user.com',
          'custom:role': 'User',
        },
      },
      response: {},
    }

    const cognitoUserEventPhone = {
      request: {
        userAttributes: {
          // eslint-disable-next-line @typescript-eslint/camelcase
          phone_number: '+14514319874',
          'custom:role': 'User',
        },
      },
      response: {},
    }

    it('enrich rawMessage if sign up with email and if requires confirmation', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['email'],
          skipConfirmation: false,
        },
      }

      const enrichedRawMessage = enrichRawMessage(config, cognitoUserEventEmail as any)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(false)
      expect(enrichedRawMessage.response.autoVerifyEmail).to.be.eq(false)
    })

    it('enrich rawMessage if sign up with email and if confirmation is skipped', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['email'],
          skipConfirmation: true,
        },
      }

      const enrichedRawMessage = enrichRawMessage(config, cognitoUserEventEmail as any)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(true)
      expect(enrichedRawMessage.response.autoVerifyEmail).to.be.eq(true)
    })

    it('enrich rawMessage if sign up with phone number and if requires confirmation', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['phone', 'email'],
        },
      }

      const enrichedRawMessage = enrichRawMessage(config, cognitoUserEventPhone as any)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(false)
      expect(enrichedRawMessage.response.autoVerifyPhone).to.be.eq(false)
    })

    it('enrich rawMessage if sign up with phone number and confirmation is skipped', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['phone', 'email'],
          skipConfirmation: true,
        },
      }

      const enrichedRawMessage = enrichRawMessage(config, cognitoUserEventPhone as any)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(true)
      expect(enrichedRawMessage.response.autoVerifyPhone).to.be.eq(true)
    })
  })
})
