import { fake } from 'sinon'
import { EncryptedTokenVerifier } from '../../../src/services/token-verifiers/encrypted-token-verifier'
import { expect } from '../../expect'

const rewire = require('rewire')
const jose = rewire('jose')

describe('EncryptedTokenVerifier', () => {
  context('when no rolesclaim is provided', () => {
    it('resolves the decryption key and calls `jwtDecrypt`', async () => {
      const fakeDecryptionKey = { type: 'fakeDecryptionKey' }
      const decryptionKeyResolver = Promise.resolve(fakeDecryptionKey)
      const fakeDecodedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123' } }
      const undoRewire = jose.__set__('jwtDecrypt', fake.resolves(fakeDecodedToken))

      const verifier = new EncryptedTokenVerifier('issuer', decryptionKeyResolver)

      expect(verifier.rolesClaim).to.be.equal('custom:role') // Sets the default rolesClaim

      await expect(verifier.verify('token')).to.eventually.become({
        header: fakeDecodedToken.protectedHeader,
        payload: fakeDecodedToken.payload,
      })

      expect(jose.jwtDecrypt).to.have.been.calledWith('token', fakeDecryptionKey)

      undoRewire()
    })
  })

  context('when a rolesclaim is provided', () => {
    it('resolves the decryption key and calls `jwtDecrypt` with the right rolesclaim', async () => {
      const fakeDecryptionKey = { type: 'fakeDecryptionKey' }
      const decryptionKeyResolver = Promise.resolve(fakeDecryptionKey)
      const fakeDecodedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123', custom: { role: 'admin' } } }
      const undoRewire = jose.__set__('jwtDecrypt', fake.resolves(fakeDecodedToken))

      const verifier = new EncryptedTokenVerifier('issuer', decryptionKeyResolver, 'customRoles')

      expect(verifier.rolesClaim).to.be.equal('customRoles')

      await expect(verifier.verify('token')).to.eventually.become({
        header: fakeDecodedToken.protectedHeader,
        payload: fakeDecodedToken.payload,
      })

      expect(jose.jwtDecrypt).to.have.been.calledWith('token', fakeDecryptionKey)
      undoRewire()
    })
  })
})
