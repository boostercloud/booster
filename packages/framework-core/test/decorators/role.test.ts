import { expect } from 'chai'
import { Booster, Role } from '../../src'

describe('the `Role` decorator', () => {
  afterEach(() => {
    for (const roleName in Booster.config.roles) {
      delete Booster.config.roles[roleName]
    }
  })

  context('when no auth metadata is provided', () => {
    it('registers a role in the Booster configuration', () => {
      @Role({ auth: {} })
      class SomeRole {}

      expect(Booster.config.roles[SomeRole.name]).to.deep.equal({ auth: {} })
    })
  })

  context('when auth metadata is provided', () => {
    it('registers a role in the Booster configuration and sets the auth metadata', () => {
      @Role({ auth: { signUpMethods: ['email', 'phone'], skipConfirmation: true } })
      class SomeRole {}

      expect(Booster.config.roles[SomeRole.name]).to.deep.equal({
        auth: { signUpMethods: ['email', 'phone'], skipConfirmation: true },
      })
    })
  })
})
