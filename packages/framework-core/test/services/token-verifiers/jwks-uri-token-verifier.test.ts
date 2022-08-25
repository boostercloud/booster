import { expect } from '../../expect'
import { JwksUriTokenVerifier } from '../../../src/services/token-verifiers/jwks-uri-token-verifier'
import * as utilities from '../../../src/services/token-verifiers/utilities'
import { fake, match, replace, restore } from 'sinon'
import { SigningKeyCallback } from 'jsonwebtoken'
import { DecodedToken } from '@boostercloud/framework-types'

describe('JwksUriTokenVerifier', () => {
  afterEach(() => {
    restore()
  })

  it('builds a key resolver and calls `verifyJWT`', async () => {
    const fakeClient = { fakeClient: true }
    replace(utilities, 'getJwksClient', fake.returns(fakeClient))
    const fakeGetKey = { fakeGetKey: true }
    replace(utilities, 'getKeyWithClient', fake.returns(fakeGetKey))
    const fakeDecodedToken = { header: { kid: '123' }, payload: { sub: '123' } }
    const fakeHeader = { header: true }
    const fakeCallback = fake()
    const fakeVerifyJWT = fake(
      (
        _token: unknown,
        _issuer: unknown,
        getKey: (header: any, callback: SigningKeyCallback) => void
      ): Promise<DecodedToken> => {
        getKey(fakeHeader, fakeCallback)
        return Promise.resolve(fakeDecodedToken)
      }
    )
    replace(utilities, 'verifyJWT', fakeVerifyJWT)

    const verifier = new JwksUriTokenVerifier('issuer', 'https://example.com/jwks')

    await expect(verifier.verify('token')).to.eventually.become(fakeDecodedToken)
    expect(utilities.getJwksClient).to.have.been.calledWith('https://example.com/jwks')
    expect(utilities.getKeyWithClient).to.have.been.calledWith(fakeClient, fakeHeader, fakeCallback)
    expect(utilities.verifyJWT).to.have.been.calledWith('token', 'issuer', match.func)
  })
})
