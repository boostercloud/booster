/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from 'chai'
import * as chai from 'chai'
import { fake, replace, restore } from 'sinon'
import { Logger, BoosterConfig } from '@boostercloud/framework-types'
import { Providers } from '../src'
import { ProviderAuthLibrary, RoleAccess, UserEnvelope } from '@boostercloud/framework-types'
import { BoosterAuth } from '../src/booster-auth'

chai.use(require('sinon-chai'))

describe('the "checkSignUp" method', () => {
  afterEach(() => {
    restore()
  })

  const logger: Logger = {
    debug() {},
    info() {},
    error() {},
  }

  function buildBoosterConfig(): BoosterConfig {
    const config = new BoosterConfig()
    config.roles['Admin'] = {
      allowSelfSignUp: false,
    }
    config.roles['Developer'] = {
      allowSelfSignUp: true,
    }
    return config
  }

  it('throws when the user has a non-existing role', () => {
    const config = buildBoosterConfig()
    const userEnvelope: UserEnvelope = {
      email: 'user@test.com',
      roles: ['Developer', 'NonExistingRole', 'Admin'],
    }

    const fakeProvider: ProviderAuthLibrary = {
      rawSignUpDataToUserEnvelope: () => userEnvelope,
    }
    const fakeGetLibrary = fake.returns(fakeProvider)
    replace(Providers, 'getLibrary', fakeGetLibrary)

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).to.throw('Unknown role NonExistingRole')
  })

  it('throws when the user has a role not allowed to self sign-up', () => {
    const config = buildBoosterConfig()
    const userEnvelope: UserEnvelope = {
      email: 'user@test.com',
      roles: ['Developer', 'Admin'],
    }

    const fakeProvider: ProviderAuthLibrary = {
      rawSignUpDataToUserEnvelope: () => userEnvelope,
    }
    const fakeGetLibrary = fake.returns(fakeProvider)
    replace(Providers, 'getLibrary', fakeGetLibrary)

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).to.throw(
      /User with role Admin can't sign up by themselves/
    )
  })

  it('succeeds user has a role allowed to self sign-up', () => {
    const config = buildBoosterConfig()
    const userEnvelope: UserEnvelope = {
      email: 'user@test.com',
      roles: ['Developer'],
    }

    const fakeProvider: ProviderAuthLibrary = {
      rawSignUpDataToUserEnvelope: () => userEnvelope,
    }
    const fakeGetLibrary = fake.returns(fakeProvider)
    replace(Providers, 'getLibrary', fakeGetLibrary)

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).not.to.throw()
  })
})

describe('the "isUserAuthorized" method', () => {
  afterEach(() => {
    restore()
  })

  // Define some roles to use in tests
  class Admin {}
  class Developer {}

  it('returns true when the "authorizedRoles" is "all"', () => {
    const authorizedRoles: RoleAccess['authorize'] = 'all'

    expect(BoosterAuth.isUserAuthorized(authorizedRoles)).to.eq(true)
  })

  it('returns false when the "authorizedRoles" is not "all" and no user was provided', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, Developer]

    expect(BoosterAuth.isUserAuthorized(authorizedRoles)).to.eq(false)
  })

  it('returns false when the user does not have any of the "authorizedRoles"', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin]
    const userEnvelope: UserEnvelope = {
      email: 'user@test.com',
      roles: ['Developer'],
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(false)
  })

  it('returns true when the user has any of the "authorizedRoles"', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, Developer]
    const userEnvelope: UserEnvelope = {
      email: 'user@test.com',
      roles: ['Developer'],
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(true)
  })
})
