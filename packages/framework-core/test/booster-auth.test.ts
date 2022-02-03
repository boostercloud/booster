import { expect } from './expect'
import { RoleAccess, UserEnvelope } from '@boostercloud/framework-types'

import { BoosterAuth } from '../src/booster-auth'

describe('the "isUserAuthorized" method', () => {
  class Admin {}
  class Developer {}
  class Reader {}

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
      username: 'user@test.com',
      roles: ['Developer'],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(false)
  })

  it('returns true when the user has any of the "authorizedRoles"', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, Developer]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: ['Developer'],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(true)
  })

  it('returns false when the user does not have any of the "authorizedRoles" and roles is a list', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: ['Developer', 'Reader'],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(false)
  })

  it('returns true when the user has any of the "authorizedRoles" and roles is a list', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, Developer]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: ['Reader', 'Developer'],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(true)
  })

  it('returns true when the user has more than one "authorizedRoles" on the roles', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, Developer, Reader]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: ['Admin', 'Reader'],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(true)
  })

  it('returns true when the user has a validr role in the middle of the "authorizedRoles" list', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin, Developer, Reader]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: ['Developer'],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(true)
  })

  it('returns false when the user does not have any of the "authorizedRoles" and roles is an empty list', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: [],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(false)
  })

  it('returns false when the user does not have any of the "authorizedRoles" and roles is a list with an empty string', () => {
    const authorizedRoles: RoleAccess['authorize'] = [Admin]
    const userEnvelope: UserEnvelope = {
      username: 'user@test.com',
      roles: [''],
      claims: { 'custom:role': 'User' },
    }

    expect(BoosterAuth.isUserAuthorized(authorizedRoles, userEnvelope)).to.eq(false)
  })
})
