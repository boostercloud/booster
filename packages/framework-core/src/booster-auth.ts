/* eslint-disable @typescript-eslint/camelcase */
import {
  BoosterConfig,
  Class,
  Logger,
  UserEnvelope,
  RoleInterface,
  RoleAccess,
  InvalidParameterError,
} from '@boostercloud/framework-types'

import * as validator from 'validator'

export class BoosterAuth {
  public static checkSignUp(rawMessage: unknown, config: BoosterConfig, logger: Logger): unknown {
    const userEnvelope = config.provider.auth.rawToEnvelope(rawMessage)
    logger.info('User envelope: ', userEnvelope)

    const roleName = userEnvelope.role
    if (roleName) {
      const roleMetadata = config.roles[userEnvelope.role]
      if (!roleMetadata) {
        throw new InvalidParameterError(`Unknown role ${roleName}`)
      }

      const authMetadata = roleMetadata.auth
      if (!authMetadata?.signUpMethods?.length) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up by themselves. Choose a different role or contact and administrator`
        )
      }

      const signUpOptions = authMetadata.signUpMethods
      const username = userEnvelope.username

      if (validator.default.isEmail(username) && !signUpOptions.includes('email')) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up with an email, a phone number is expected`
        )
      }

      if (!validator.default.isEmail(username) && !signUpOptions.includes('phone')) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up with a phone number, an email is expected`
        )
      }
    }

    return config.provider.auth.handleSignUpResult(config, rawMessage, userEnvelope)
  }

  public static isUserAuthorized(authorizedRoles: RoleAccess['authorize'], user?: UserEnvelope): boolean {
    // Is the current user authorized?
    if (authorizedRoles == 'all') {
      return true // Sure! This is public access
    }

    if (!user) {
      return false // Nope! No user signed-in and this is NOT public access
    }

    // Okay, user is signed-in and this is private access.
    // Check the roles then
    return userHasSomeRole(user, authorizedRoles)
  }
}

function userHasSomeRole(user: UserEnvelope, authorizedRoles: Array<Class<RoleInterface>>): boolean {
  return authorizedRoles.some((roleClass) => user.role === roleClass.name)
}
