import { expect } from '../../expect'
import { PublicKeyTokenVerifier } from '../../../src/services/token-verifiers/public-key-token-verifier'
import * as utilities from '../../../src/services/token-verifiers/utilities'
import { fake, replace, restore } from 'sinon'

describe('PublicKeyTokenVerifier', () => {
  afterEach(() => {
    restore()
  })

  it('resolves the public key and calls `verifyJWT`', async () => {
    const fakeDecodedToken = { header: { kid: '123' }, payload: { sub: '123' } }
    const fakeVerifyJWT = fake.resolves(fakeDecodedToken)
    replace(utilities, 'verifyJWT', fakeVerifyJWT)

    const publicKey = 'public key'
    const publicKeyResolver = Promise.resolve(publicKey)
    const verifier = new PublicKeyTokenVerifier('https://example.com/jwks', publicKeyResolver)

    await expect(verifier.verify('token')).to.eventually.become(fakeDecodedToken)
    expect(fakeVerifyJWT).to.have.been.calledWith('token', 'https://example.com/jwks', publicKey)
  })
})
