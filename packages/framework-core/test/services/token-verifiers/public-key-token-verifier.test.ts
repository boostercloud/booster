import { expect } from '../../expect'
import { PublicKeyTokenVerifier } from '../../../src/services/token-verifiers/public-key-token-verifier'
import { fake, replace, restore } from 'sinon'
import * as jose from 'jose'

describe('PublicKeyTokenVerifier', () => {
  afterEach(() => {
    restore()
  })

  context('when no algorithm or rolesclaim are provided', () => {
    it('resolves the public key and calls `verifyJWT`', async () => {
      const publicKey = 'public key'
      const publicKeyResolver = Promise.resolve(publicKey)
      const fakeSPKIKey = { type: 'somekey' }
      replace(jose, 'importSPKI', fake.resolves(fakeSPKIKey))
      const fakeDecodedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123' } }
      replace(jose, 'jwtVerify', fake.resolves(fakeDecodedToken))

      const verifier = new PublicKeyTokenVerifier('issuer', publicKeyResolver)

      expect(verifier.rolesClaim).to.be.equal('custom:role') // Sets the default rolesClaim

      await expect(verifier.verify('token')).to.eventually.become(fakeDecodedToken)

      expect(jose.importSPKI).to.have.been.calledWith(publicKey, 'RS256') // default algorithm
      expect(jose.jwtVerify).to.have.been.calledWith('token', fakeSPKIKey, { issuer: 'issuer', algorithms: ['RS256'] })
    })
  })

  context('when an algorithm is provided', () => {
    it('resolves the public key and calls `verifyJWT` with the right algorithm', async () => {
      const publicKey = 'public key'
      const publicKeyResolver = Promise.resolve(publicKey)
      const fakeSPKIKey = { type: 'somekey' }
      replace(jose, 'importSPKI', fake.resolves(fakeSPKIKey))
      const fakeDecodedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123' } }
      replace(jose, 'jwtVerify', fake.resolves(fakeDecodedToken))

      const verifier = new PublicKeyTokenVerifier('issuer', publicKeyResolver, undefined, 'RS384')

      expect(verifier.rolesClaim).to.be.equal('custom:role') // Sets the default rolesClaim

      await expect(verifier.verify('token')).to.eventually.become(fakeDecodedToken)

      expect(jose.importSPKI).to.have.been.calledWith(publicKey, 'RS384')
      expect(jose.jwtVerify).to.have.been.calledWith('token', fakeSPKIKey, { issuer: 'issuer', algorithms: ['RS384'] })
    })
  })

  context('when a rolesclaim is provided', () => {
    it('resolves the public key and calls `verifyJWT` with the right rolesclaim', async () => {
      const publicKey = 'public key'
      const publicKeyResolver = Promise.resolve(publicKey)
      const fakeSPKIKey = { type: 'somekey' }
      replace(jose, 'importSPKI', fake.resolves(fakeSPKIKey))
      const fakeDecodedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123', custom: { role: 'admin' } } }
      replace(jose, 'jwtVerify', fake.resolves(fakeDecodedToken))

      const verifier = new PublicKeyTokenVerifier('issuer', publicKeyResolver, 'customRoles')

      expect(verifier.rolesClaim).to.be.equal('customRoles')

      await expect(verifier.verify('token')).to.eventually.become(fakeDecodedToken)

      expect(jose.importSPKI).to.have.been.calledWith(publicKey, 'RS256')
      expect(jose.jwtVerify).to.have.been.calledWith('token', fakeSPKIKey, {
        issuer: 'issuer',
        algorithms: ['RS256'],
        audience: 'custom:role',
      })
    })
  })
})
