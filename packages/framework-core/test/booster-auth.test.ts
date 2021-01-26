/* eslint-disable @typescript-eslint/camelcase */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from './expect'
import { RoleAccess, UserEnvelope } from '@boostercloud/framework-types'

import { BoosterAuth } from '../src/booster-auth'

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
