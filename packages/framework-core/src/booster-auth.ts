import {
  BoosterConfig,
  Class,
  Logger,
  ProviderAuthLibrary,
  UserEnvelope,
  RoleInterface,
  RoleAccess,
  InvalidParameterError,
} from '@boostercloud/framework-types'

export class BoosterAuth {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  public static checkSignUp(rawMessage: any, config: BoosterConfig, logger: Logger): any {
    const provider: ProviderAuthLibrary = config.provider
    const userEnvelope = provider.rawSignUpDataToUserEnvelope(rawMessage)
    logger.info('User envelope: ', userEnvelope)

    userEnvelope.roles.forEach((roleName: string) => {
      const roleMetadata = config.roles[roleName]
      if (!roleMetadata) {
        throw new InvalidParameterError(`Unknown role ${roleName}`)
      }
      if (!roleMetadata.allowSelfSignUp) {
        throw new InvalidParameterError(
          `User with role ${roleName} can't sign up by themselves. Choose a different role or contact and administrator`
        )
      }
    })

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
  return authorizedRoles.some((roleClass) => user.roles.includes(roleClass.name))
}
