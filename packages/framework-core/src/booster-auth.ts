import {
  BoosterConfig,
  Class,
  Logger,
  UserEnvelope,
  RoleInterface,
  RoleAccess,
  InvalidParameterError,
} from '@boostercloud/framework-types'

export class BoosterAuth {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public static checkSignUp(rawMessage: any, config: BoosterConfig, logger: Logger): any {
    const userEnvelope = config.provider.auth.rawToEnvelope(rawMessage)
    logger.info('User envelope: ', userEnvelope)

    const roleName = userEnvelope.role
    if (roleName) {
      const roleMetadata = config.roles[userEnvelope.role]
      if (!roleMetadata) {
        throw new InvalidParameterError(`Unknown role ${roleName}`)
      }

      const authenticationMetadata = roleMetadata.authentication
      if (
        !authenticationMetadata ||
        !authenticationMetadata.signUpMethods ||
        (Array.isArray(authenticationMetadata.signUpMethods) && !authenticationMetadata.signUpMethods.length)
      ) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up by themselves. Choose a different role or contact and administrator`
        )
      }

      const signUpOptions = authenticationMetadata.signUpMethods
      const username = userEnvelope.username

      if (signUpOptions === 'phone' && username.type === 'email') {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up with an email, a phone number is expected`
        )
      } else if (signUpOptions === 'email' && username.type === 'phone') {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up with a phone number, an email is expected`
        )
      }
    }

    return rawMessage
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
