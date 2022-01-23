/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from './expect'
import { BoosterConfig, UserEnvelope } from '@boostercloud/framework-types'
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

  config.tokenVerifiers = [
    {
      issuer,
      jwksUri: auth0VerifierUri + '.well-known/jwks.json',
    },
  ]

  beforeEach(() => {
    jwks.start()
    boosterTokenVerifier = new BoosterTokenVerifier(config)
  })

  afterEach(async () => {
    await jwks.stop()
  })

  it('accepts custom claims and generates a UserEnvelope with them', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      extraParam: 'claims',
      anotherParam: 111,
      email,
      phoneNumber,
    })

    const expectedUser: UserEnvelope = {
      id: userId,
      username: email,
      role: 'User',
      claims: {
        sub: userId,
        iss: issuer,
        'custom:role': 'User',
        extraParam: 'claims',
        anotherParam: 111,
        email,
        phoneNumber,
      },
    }

    const user = await boosterTokenVerifier.verify(token)

    expect(user).to.deep.equals(expectedUser)
  })

  it('decode and verify an auth token with the custom roles', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      email,
      phoneNumber,
    })

    const expectedUser: UserEnvelope = {
      id: userId,
      username: email,
      role: 'User',
      claims: {
        sub: userId,
        iss: issuer,
        'custom:role': 'User',
        email,
        phoneNumber,
      },
    }

    const user = await boosterTokenVerifier.verify(token)

    expect(user).to.deep.equals(expectedUser)
  })

  it('fails if a different issuer emitted the token', async () => {
    const token = jwks.token({
      iss: 'firebase',
    })

    const verifyFunction = boosterTokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejected
  })

  it('fails if a token has expired', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      email: email,
      phoneNumber,
      exp: 0,
    })

    const verifyFunction = boosterTokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejected
  })

  it("fails if extra validation doesn't match", async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      email: email,
      phoneNumber,
    })

    const configWithExtraValidation = new BoosterConfig('test with extra validation')
    configWithExtraValidation.tokenVerifiers = [
      {
        issuer,
        jwksUri: auth0VerifierUri + '.well-known/jwks.json',
        extraValidation: (_headers, payload) => {
          if ((payload as any)?.['custom:role'] !== 'Admin') {
            throw 'Unauthorized'
          }
        },
      },
    ]

    const tokenVerifier = new BoosterTokenVerifier(configWithExtraValidation)
    const verifyFunction = tokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejectedWith('Unauthorized')
  })

  it("fails if extra validation for token headers doesn't match", async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
    })

    const configWithExtraValidation = new BoosterConfig('test with extra validation')
    configWithExtraValidation.tokenVerifiers = [
      {
        issuer,
        jwksUri: auth0VerifierUri + '.well-known/jwks.json',
        extraValidation: (headers) => {
          if ((headers as any)?.alg !== 'RS512') {
            throw 'Invalid token encoding'
          }
        },
      },
    ]

    const tokenVerifier = new BoosterTokenVerifier(configWithExtraValidation)
    const verifyFunction = tokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejectedWith('Invalid token encoding')
  })

})
