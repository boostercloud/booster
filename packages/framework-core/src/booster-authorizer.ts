import { Class, UserEnvelope, RoleInterface, NotAuthorizedError } from '@boostercloud/framework-types'

export class BoosterAuthorizer {
  public static allowAccess(): Promise<void> {
    return Promise.resolve()
  }

  public static denyAccess(): Promise<void> {
    return Promise.reject(new NotAuthorizedError('Access denied for this resource'))
  }

  public static authorizeRoles(authorizedRoles: Array<Class<RoleInterface>>, user?: UserEnvelope): Promise<void> {
    if (user && userHasSomeRole(user, authorizedRoles)) {
      return this.allowAccess()
    }
    return this.denyAccess()
  }
}

function userHasSomeRole(user: UserEnvelope, authorizedRoles: Array<Class<RoleInterface>>): boolean {
  return authorizedRoles.some((roleClass) => user.roles?.includes(roleClass.name))
}
