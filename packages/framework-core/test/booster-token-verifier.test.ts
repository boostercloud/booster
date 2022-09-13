/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from './expect'
import { BoosterConfig, UserEnvelope } from '@boostercloud/framework-types'
import createJWKSMock from 'mock-jwks'
import { internet, phone, random } from 'faker'
import { BoosterTokenVerifier } from '../src/booster-token-verifier'
import { JwksUriTokenVerifier } from '../src/services/token-verifiers/jwks-uri-token-verifier'
import { DecodedToken } from '../../framework-types/src/concepts/token-verifier'

describe('the "verifyToken" method', () => {
  const auth0VerifierUri = 'https://myauth0app.auth0.com/'
  const issuer = 'auth0'
  const jwks = createJWKSMock(auth0VerifierUri)
  const email = internet.email()
  const phoneNumber = phone.phoneNumber()
  const userId = random.uuid()
  const config = new BoosterConfig('test')
  let boosterTokenVerifier: BoosterTokenVerifier

  config.tokenVerifiers = [new JwksUriTokenVerifier(issuer, auth0VerifierUri + '.well-known/jwks.json')]

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
      roles: ['User'],
      claims: {
        sub: userId,
        iss: issuer,
        'custom:role': 'User',
        extraParam: 'claims',
        anotherParam: 111,
        email,
        phoneNumber,
      },
      header: {
        alg: 'RS256',
      },
    }

    const user = await boosterTokenVerifier.verify(token)

    expect(user.claims).to.deep.equals(expectedUser.claims)
    expect(user.header?.alg).equals(expectedUser.header?.alg)
    expect(user.roles).to.have.all.members(expectedUser.roles)
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
      roles: ['User'],
      claims: {
        sub: userId,
        iss: issuer,
        'custom:role': 'User',
        email,
        phoneNumber,
      },
      header: {
        alg: 'RS256',
      },
    }

    const user = await boosterTokenVerifier.verify(token)

    expect(user.claims).to.deep.equals(expectedUser.claims)
    expect(user.header?.alg).equals(expectedUser.header?.alg)
    expect(user.roles).to.have.all.members(expectedUser.roles)
  })

  it('decode and verify an auth token with an empty custom role', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': '',
      email,
      phoneNumber,
    })

    const expectedUser: UserEnvelope = {
      id: userId,
      username: email,
      roles: [],
      claims: {
        sub: userId,
        iss: issuer,
        'custom:role': '',
        email,
        phoneNumber,
      },
      header: {
        alg: 'RS256',
      },
    }

    const user = await boosterTokenVerifier.verify(token)

    expect(user.claims).to.deep.equals(expectedUser.claims)
    expect(user.header?.alg).equals(expectedUser.header?.alg)
    expect(user.roles).to.have.all.members(expectedUser.roles)
  })

  it('decode and verify an auth token with a list of custom roles', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': ['User', 'Other'],
      email,
      phoneNumber,
    })

    const expectedUser: UserEnvelope = {
      id: userId,
      username: email,
      roles: ['User', 'Other'],
      claims: {
        sub: userId,
        iss: issuer,
        'custom:role': ['User', 'Other'],
        email,
        phoneNumber,
      },
      header: {
        alg: 'RS256',
      },
    }

    const user = await boosterTokenVerifier.verify(token)

    expect(user.claims).to.deep.equals(expectedUser.claims)
    expect(user.header?.alg).equals(expectedUser.header?.alg)
    expect(user.roles).to.have.all.members(expectedUser.roles)
  })

  it('fails if role is a number', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 123,
      email,
      phoneNumber,
    })

    const verifyFunction = boosterTokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejectedWith(
      'Error: Invalid role format 123. Valid format are Array<string> or string'
    )
  })

  it('fails if role is not a list of strings', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': ['a', 'b', 123],
      email,
      phoneNumber,
    })

    const user = boosterTokenVerifier.verify(token)

    await expect(user).to.eventually.be.rejectedWith(
      'Error: Invalid role format 123. Valid format are Array<string> or string'
    )
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

    await expect(verifyFunction).to.eventually.be.rejectedWith('jwt expired')
  })

  it('fails if current time is before the notBefore claim of the token ', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': ['User', 'Other'],
      email,
      phoneNumber,
      nbf: Math.floor(Date.now() / 1000) + 999_999,
    })

    const verifyFunction = boosterTokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejectedWith('jwt not active')
  })

  it("fails if extra validation doesn't match", async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
      'custom:role': 'User',
      email: email,
      phoneNumber,
    })

    class ExtendedJwksUriTokenVerifier extends JwksUriTokenVerifier {
      async verify(token: string): Promise<DecodedToken> {
        const decodedToken = await super.verify(token)

        const promiseSolved = await Promise.resolve()
        console.log(promiseSolved)
        if (decodedToken.payload['custom:role'] !== 'Admin') {
          throw 'Unauthorized'
        }

        return decodedToken
      }
    }

    const configWithExtraValidation = new BoosterConfig('test with extra validation')
    configWithExtraValidation.tokenVerifiers = [
      new ExtendedJwksUriTokenVerifier(issuer, auth0VerifierUri + '.well-known/jwks.json'),
    ]

    const tokenVerifier = new BoosterTokenVerifier(configWithExtraValidation)
    const verifyFunction = tokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejectedWith('Unauthorized')
  })

  it('fails if a custom token verifier raises an exception', async () => {
    const token = jwks.token({
      sub: userId,
      iss: issuer,
    })

    class ExtendedJwksUriTokenVerifier2 extends JwksUriTokenVerifier {
      async verify(token: string): Promise<DecodedToken> {
        const decodedToken = await super.verify(token)

        const header = decodedToken.header
        const promiseSolved = await Promise.resolve()
        console.log(promiseSolved)
        if (header?.alg !== 'RS512') {
          throw 'Invalid token encoding'
        }

        return decodedToken
      }
    }

    const configWithExtraValidation = new BoosterConfig('test with extra validation')
    configWithExtraValidation.tokenVerifiers = [
      new ExtendedJwksUriTokenVerifier2(issuer, auth0VerifierUri + '.well-known/jwks.json'),
    ]

    const tokenVerifier = new BoosterTokenVerifier(configWithExtraValidation)
    const verifyFunction = tokenVerifier.verify(token)

    await expect(verifyFunction).to.eventually.be.rejectedWith('Invalid token encoding')
  })
})
