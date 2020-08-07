/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from './expect'
import { restore, fake, replace } from 'sinon'
import { Logger, BoosterConfig } from '@boostercloud/framework-types'
import { RoleAccess, UserEnvelope } from '@boostercloud/framework-types'
import { BoosterAuth } from '../src/booster-auth'
import { ProviderLibrary } from '@boostercloud/framework-types'

const logger: Logger = {
  debug() {},
  info() {},
  error() {},
}

describe('the "checkSignUp" method', () => {
  afterEach(() => {
    restore()
  })

  function buildBoosterConfig(): BoosterConfig {
    const config = new BoosterConfig('test')
    config.provider = ({
      auth: { rawToEnvelope: () => {} },
    } as unknown) as ProviderLibrary
    config.roles['Admin'] = {
      authentication: {
        signUpMethods: [],
      },
    }
    config.roles['UserWithEmail'] = {
      authentication: {
        signUpMethods: ['email'],
      },
    }

    config.roles['UserWithPhone'] = {
      authentication: {
        signUpMethods: ['phone'],
      },
    }

    config.roles['SuperUser'] = {
      authentication: {
        signUpMethods: ['phone', 'email'],
      },
    }

    return config
  }

  it('throws an error when the user has a non-existing role', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'NonExistingRole',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).to.throw('Unknown role NonExistingRole')
  })

  it('throws a error when the user has a role not allowed to self sign-up', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'Admin',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).to.throw(
      /User with role Admin can't sign up by themselves/
    )
  })

  it('succeeds user to sign up with email when SignUpOptions has email as value', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'UserWithEmail',
        username: 'test@gmail.com',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).not.to.throw()
  })

  it('succeeds user to sign up with phone when SignUpOptions has phone as value', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'UserWithPhone',
        username: '+59165783459',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).not.to.throw()
  })

  it('succeeds user to sign up with email when SignUpOptions has phone and email as value', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'SuperUser',
        username: 'test@gmail.com',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).not.to.throw()
  })

  it('succeeds user to sign up with phone when SignUpOptions has phone and email as value', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'SuperUser',
        username: '+59165783459',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).not.to.throw()
  })

  it('throws an error when user signs up with phone but SignUpOptions has email as value', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'UserWithEmail',
        username: '+59165783459',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).to.throw(
      /User with role UserWithEmail can't sign up with a phone number, an email is expected/
    )
  })

  it('throws an error when user signs up with email but SignUpOptions has phone as value', () => {
    const config = buildBoosterConfig()
    replace(
      config.provider.auth,
      'rawToEnvelope',
      fake.returns({
        role: 'UserWithPhone',
        username: 'test@gmail.com',
      })
    )

    expect(() => BoosterAuth.checkSignUp({}, config, logger)).to.throw(
      /User with role UserWithPhone can't sign up with an email, a phone number is expected/
    )
  })
})

describe('the "isUserAuthorized" method', () => {
  // Define some roles to use in tests
  class Admin {}
  class UserWithEmail {}

  it('returns true when the "authorizedRoles" is "all"', () => {
    const authorizedRoles: RoleAccess['authorize'] = 'all'

    expect(BoosterAuth.isUserAuthorized(authorizedRoles)).to.eq(true)
  })

  it('returns false when the "authorizedRoles" is not "all" and no user was provided', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, UserWithEmail]

    expect(BoosterAuth.isUserAuthorized(authorizedRoles)).to.eq(false)
  })

  it('returns false when the user does not have any of the "authorizedRoles"', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      role: 'UserWithEmail',
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(false)
  })

  it('returns true when the user has any of the "authorizedRoles"', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, UserWithEmail]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      role: 'UserWithEmail',
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(true)
  })
})
