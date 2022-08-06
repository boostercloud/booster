import { expect } from '../../expect'
import { JwksUriTokenVerifier } from '../../../src/services/token-verifiers/jwks-uri-token-verifier'
import * as jose from 'jose'
import { fake, replace, restore } from 'sinon'

describe('JwksUriTokenVerifier', () => {
  afterEach(() => {
    restore()
  })

  context('when no algorithm or rolesClaim are provided', () => {
    it('builds a key resolver and calls `jwtVerify` with the right JWKSet and the default algorithm', async () => {
      const fakeJWKS = { someKeys: true }
      replace(jose, 'createRemoteJWKSet', fake.returns(fakeJWKS))
      const fakeVerifiedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123' } }
      replace(jose, 'jwtVerify', fake.resolves(fakeVerifiedToken))

      const verifier = new JwksUriTokenVerifier('issuer', 'https://example.com/jwks')

      expect(verifier.rolesClaim).to.be.equal('custom:role') // Sets the default rolesClaim

      await expect(verifier.verify('token')).to.eventually.become({
        payload: fakeVerifiedToken.payload,
        header: fakeVerifiedToken.protectedHeader,
      })

      expect(jose.createRemoteJWKSet).to.have.been.calledWithMatch({ origin: 'https://example.com/jwks' })
      expect(jose.jwtVerify).to.have.been.calledWith('token', fakeJWKS, { issuer: 'issuer', algorithms: ['RS256'] })
    })
  })

  context('when rolesClaim is provided', () => {
    it('builds a key resolver and calls `jwtVerify` with the right JWKSet and the default algorithm and sets the roleClaims attribute', async () => {
      const fakeJWKS = { someKeys: true }
      replace(jose, 'createRemoteJWKSet', fake.returns(fakeJWKS))
      const fakeVerifiedToken = {
        protectedHeader: { kid: '123' },
        payload: { sub: '123', customRoles: ['role1', 'role2'] },
      }
      replace(jose, 'jwtVerify', fake.resolves(fakeVerifiedToken))

      const verifier = new JwksUriTokenVerifier('issuer', 'https://example.com/jwks', 'customRoles')

      expect(verifier.rolesClaim).to.equal('customRoles')

      await expect(verifier.verify('token')).to.eventually.become({
        payload: fakeVerifiedToken.payload,
        header: fakeVerifiedToken.protectedHeader,
      })

      expect(jose.createRemoteJWKSet).to.have.been.calledWithMatch({ origin: 'https://example.com/jwks' })
      expect(jose.jwtVerify).to.have.been.calledWith('token', fakeJWKS, { issuer: 'issuer', algorithms: ['RS256'] })
    })
  })

  context('when an algorithm is provided', () => {
    it('builds a key resolver and calls `jwtVerify` with the right JWKSet and the provided algorithm', async () => {
      const fakeJWKS = { someKeys: true }
      replace(jose, 'createRemoteJWKSet', fake.returns(fakeJWKS))
      const fakeVerifiedToken = { protectedHeader: { kid: '123' }, payload: { sub: '123' } }
      replace(jose, 'jwtVerify', fake.resolves(fakeVerifiedToken))

      const verifier = new JwksUriTokenVerifier('issuer', 'https://example.com/jwks', undefined, 'ES256')

      expect(verifier.rolesClaim).to.be.equal('custom:role') // Sets the default rolesClaim

      await expect(verifier.verify('token')).to.eventually.become({
        payload: fakeVerifiedToken.payload,
        header: fakeVerifiedToken.protectedHeader,
      })

      expect(jose.createRemoteJWKSet).to.have.been.calledWithMatch({ origin: 'https://example.com/jwks' })
      expect(jose.jwtVerify).to.have.been.calledWith('token', fakeJWKS, { issuer: 'issuer', algorithms: ['ES256'] })
    })
  })
})
