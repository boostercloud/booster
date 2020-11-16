/* eslint-disable @typescript-eslint/camelcase */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from './expect'
import { BoosterConfig } from '@boostercloud/framework-types'
import { UserEnvelope } from '@boostercloud/framework-types'
import createJWKSMock from 'mock-jwks'
import { internet, phone, random } from 'faker'
import { BoosterTokenVerifier } from '../src/booster-token-verifier'

describe('the "verifyToken" method', () => {
  const auth0VerifierUri = 'https://myauth0app.auth0.com/'
  const issuer = 'auth0'
  const jwks = createJWKSMock(auth0VerifierUri)
  const email = internet.email()
  const phoneNumber = phone.phoneNumber()
  const userId = random.uuid()
  const config = new BoosterConfig('test')
  let boosterTokenVerifier: BoosterTokenVerifier

  config.tokenVerifier = {
    issuer,
    jwksUri: auth0VerifierUri + '.well-known/jwks.json',
  }

  beforeEach(() => {
    jwks.start()
    boosterTokenVerifier = new BoosterTokenVerifier(config)
  })

  afterEach(async () => {
    await jwks.stop()
  })

  it('decode and verify an auth token with the custom roles', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      email: email,
      phone_number: phoneNumber,
    })

    const expectedUser: UserEnvelope = {
      id: userId,
      username: email,
      role: 'User',
    }

    expect(await boosterTokenVerifier.verify(token)).to.deep.equals(expectedUser)
  })

  it('fails if a different issuer emitted the token', async () => {
    const token = jwks.token({
      iss: 'firebase',
    })

    await expect(boosterTokenVerifier.verify(token)).to.eventually.be.rejected
  })

  it('fails if a token has expired', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      email: email,
      phone_number: phoneNumber,
      exp: 0,
    })

    await expect(boosterTokenVerifier.verify(token)).to.eventually.be.rejected
  })
})
