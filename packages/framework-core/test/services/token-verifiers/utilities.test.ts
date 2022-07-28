/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../../expect'
import { fake, match, replace, restore } from 'sinon'
import { getJwksClient, getKeyWithClient, verifyJWT } from '../../../src/services/token-verifiers/utilities'
import { JwksClient } from 'jwks-rsa'
import * as jwt from 'jsonwebtoken'
import { DecodedToken } from '@boostercloud/framework-types'

describe('function `getJwksClient`', () => {
  it('returns a JwksClient instance', () => {
    const fakeJwksClient = fake.returns({})

    // This is a workaround to stub the default export function from `jwks-rsa`
    require('jwks-rsa')
    delete require.cache[require.resolve('jwks-rsa')]
    require.cache[require.resolve('jwks-rsa')] = {
      exports: fakeJwksClient,
    } as NodeModule

    getJwksClient('https://example.com/jwks')
    expect(fakeJwksClient).to.have.been.calledWith({
      jwksUri: 'https://example.com/jwks',
      cache: true,
      cacheMaxAge: 15 * 60 * 1000,
    })

    // Undo the workaround
    delete require.cache[require.resolve('jwks-rsa')]
  })
})

describe('function `getKeyWithClient`', () => {
  context('when the header does not include a "kid" property', () => {
    it('calls the callback function with an error', () => {
      const fakeJwksClient = {} as JwksClient
      const fakeHeader = {} as jwt.JwtHeader
      const fakeCallback = fake()

      getKeyWithClient(fakeJwksClient, fakeHeader, fakeCallback)

      expect(fakeCallback).to.have.been.calledWithMatch({ message: 'JWT kid not found' })
    })
  })

  context('when getting the public key fails', () => {
    it('calls the callback function with an error', () => {
      // eslint-disable-next-line @typescript-eslint/ban-types
      const fakeGetSigningKeyCallback = fake((_kid: unknown, callback: Function) =>
        callback(new Error('Error getting public key'))
      )
      const fakeJwksClient = {
        getSigningKey: fakeGetSigningKeyCallback,
      } as unknown as JwksClient
      const fakeHeader = { kid: '123' } as jwt.JwtHeader
      const fakeCallback = fake()

      getKeyWithClient(fakeJwksClient, fakeHeader, fakeCallback)

      expect(fakeCallback).to.have.been.calledWithMatch({ message: 'Error getting public key' })
    })
  })

  context('when getting the public key succeeds', () => {
    it('calls the callback function with the public key', () => {
      // eslint-disable-next-line @typescript-eslint/ban-types
      const fakeGetSigningKeyCallback = fake((_kid: string, callback: Function) =>
        callback(null, { getPublicKey: () => 'public-key' })
      )
      const fakeJwksClient = {
        getSigningKey: fakeGetSigningKeyCallback,
      } as unknown as JwksClient
      const fakeHeader = { kid: '123' } as jwt.JwtHeader
      const fakeCallback = fake()

      getKeyWithClient(fakeJwksClient, fakeHeader, fakeCallback)

      expect(fakeCallback).to.have.been.calledWith(null, 'public-key')
    })
  })
})

describe('function `verifyJWT`', () => {
  afterEach(() => {
    restore()
  })

  context('when the token is verified', () => {
    it('resolves to a decoded token', async () => {
      const fakeToken = 'Bearer token'
      const fakeIssuer = 'issuer'
      const fakePublicKey = 'public-key'
      const fakeDecodedToken = { a: 'token' } as unknown as DecodedToken
      // eslint-disable-next-line @typescript-eslint/ban-types
      const fakeVerify = fake((_token: unknown, _key: unknown, _options: unknown, callback: Function) =>
        callback(null, fakeDecodedToken)
      )
      replace(jwt, 'verify', fakeVerify as any)

      await expect(verifyJWT(fakeToken, 'issuer', fakePublicKey)).to.eventually.become(fakeDecodedToken)

      expect(fakeVerify).to.have.been.calledWith(
        'token',
        fakePublicKey,
        {
          algorithms: ['RS256'],
          issuer: fakeIssuer,
          complete: true,
        },
        match.func
      )
    })
  })

  context('when the token is not verified', () => {
    it('rejects with an error', async () => {
      const fakeToken = 'Bearer token'
      const fakeIssuer = 'issuer'
      const fakePublicKey = 'public-key'
      const fakeError = new Error('Error verifying token')
      // eslint-disable-next-line @typescript-eslint/ban-types
      const fakeVerify = fake((_token: unknown, _key: unknown, _options: unknown, callback: Function) =>
        callback(fakeError)
      )
      replace(jwt, 'verify', fakeVerify as any)

      await expect(verifyJWT(fakeToken, 'issuer', fakePublicKey)).to.eventually.be.rejectedWith(fakeError)

      expect(fakeVerify).to.have.been.calledWith(
        'token',
        fakePublicKey,
        {
          algorithms: ['RS256'],
          issuer: fakeIssuer,
          complete: true,
        },
        match.func
      )
    })
  })
})
