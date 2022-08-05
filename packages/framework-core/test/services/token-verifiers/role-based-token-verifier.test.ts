import { DecodedToken } from '@boostercloud/framework-types'
import {
  DEFAULT_ROLES_CLAIM,
  RoleBasedTokenVerifier,
} from '../../../src/services/token-verifiers/role-based-token-verifier'
import { expect } from '../../expect'

describe('DEFAULT_ROLES_CLAIM', () => {
  it('should be "custom:role"', () => {
    expect(DEFAULT_ROLES_CLAIM).to.equal('custom:role')
  })
})

describe('abstract class RoleBasedTokenVerifier', () => {
  context('a class that extends it', () => {
    context('when the roles claim is not set', () => {
      it('can build a `UserEnvelope` from a decoded token taking the roles from the default roles claim', async () => {
        const roles = ['ProUser']
        const header = {
          kid: 'kid123',
        }
        const payload = {
          sub: 'sub123',
          email: 'morenito19@example.com',
          'custom:role': roles,
        }
        class UselessTokenVerifier extends RoleBasedTokenVerifier {
          public constructor() {
            super()
          }

          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public verify(_token: string): Promise<DecodedToken> {
            return Promise.resolve({ header, payload })
          }
        }

        const verifier = new UselessTokenVerifier()
        const decodedToken = await verifier.verify('123')
        const userEnvelope = verifier.toUserEnvelope(decodedToken)

        expect(userEnvelope).to.deep.equal({
          id: 'sub123',
          username: 'morenito19@example.com',
          roles,
          claims: payload,
          header,
        })
      })
    })

    context('when the roles claim is set', () => {
      it('can build a `UserEnvelope` from a decoded token taking the roles from the roles claim', async () => {
        const roles = ['ProUser']
        const header = {
          kid: 'kid123',
        }
        const payload = {
          sub: 'sub123',
          email: 'morenito19@example.com',
          ekipaso: roles,
        }
        class UselessTokenVerifier extends RoleBasedTokenVerifier {
          public constructor() {
            super('ekipaso')
          }

          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          public verify(_token: string): Promise<DecodedToken> {
            return Promise.resolve({ header, payload })
          }
        }

        const verifier = new UselessTokenVerifier()
        const decodedToken = await verifier.verify('123')
        const userEnvelope = verifier.toUserEnvelope(decodedToken)

        expect(userEnvelope).to.deep.equal({
          id: 'sub123',
          username: 'morenito19@example.com',
          roles,
          claims: payload,
          header,
        })
      })
    })
  })
})
