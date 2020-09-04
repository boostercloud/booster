/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import {
  rawSignUpDataToUserEnvelope,
  userEnvelopeFromAuthToken,
  handleSignUpResult,
} from '../../src/library/auth-adapter'
import { UserEnvelope, BoosterConfig } from '@boostercloud/framework-types'
import { fake, replace, restore } from 'sinon'
import { AttributeListType } from 'aws-sdk/clients/cognitoidentityserviceprovider'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { random } from 'faker'

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

  describe('the userEnvelopeFromAuthToken function', () => {
    afterEach(() => {
      restore()
    })

    it('returns nothing when no token is provided', async () => {
      const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
      const token = undefined
      await expect(userEnvelopeFromAuthToken(userPool, token)).to.be.eventually.equal(undefined)
    })

    it('returns a user when the corresponding token is provided', async () => {
      const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
      const token = random.uuid()
      const userReturnedFromPool: AttributeListType = [
        {
          Name: 'email',
          Value: 'test@user.com',
        },
        {
          Name: 'custom:role',
          Value: 'User',
        },
      ]
      const expectedUser = {
        username: 'test@user.com',
        role: 'User',
      }

      replace(
        userPool,
        'getUser',
        fake.returns({
          promise: fake.resolves({
            UserAttributes: userReturnedFromPool,
          }),
        })
      )
      const bearerToken = `Bearer ${token}`
      await expect(userEnvelopeFromAuthToken(userPool, bearerToken)).to.be.eventually.deep.equal(expectedUser)
      expect(userPool.getUser).to.have.been.calledWith({
        AccessToken: token,
      })
    })
  })

  describe('the `handleSignUpResult`', () => {
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

    const userEnvelopeEmail = {
      username: 'test@user.com',
      role: 'User',
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

    const userEnvelopePhone = {
      username: '+14514319874',
      role: 'User',
    }

    it('enrich rawMessage if sign up with email and if requires confirmation', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['email'],
          skipConfirmation: false,
        },
      }

      const enrichedRawMessage = handleSignUpResult(config, cognitoUserEventEmail as any, userEnvelopeEmail)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(false)
    })

    it('enrich rawMessage if sign up with email and if confirmation is skipped', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['email'],
          skipConfirmation: true,
        },
      }

      const enrichedRawMessage = handleSignUpResult(config, cognitoUserEventEmail as any, userEnvelopeEmail)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(true)
    })

    it('enrich rawMessage if sign up with phone number and if requires confirmation', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['phone', 'email'],
        },
      }

      const enrichedRawMessage = handleSignUpResult(config, cognitoUserEventPhone as any, userEnvelopePhone)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(false)
    })

    it('enrich rawMessage if sign up with phone number and confirmation is skipped', async () => {
      config.roles['User'] = {
        auth: {
          signUpMethods: ['phone', 'email'],
          skipConfirmation: true,
        },
      }

      const enrichedRawMessage = handleSignUpResult(config, cognitoUserEventPhone as any, userEnvelopePhone)
      expect(enrichedRawMessage.response.autoConfirmUser).to.be.eq(true)
    })
  })
})
