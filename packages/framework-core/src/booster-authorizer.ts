import {
  Class,
  UserEnvelope,
  RoleInterface,
  NotAuthorizedError,
  QueryAuthorizer,
  CommandRoleAccess,
  QueryRoleAccess,
  ReadModelRoleAccess,
  CommandAuthorizer,
  ReadModelAuthorizer,
  HealthRoleAccess,
  HealthAuthorizer,
} from '@boostercloud/framework-types'

export class BoosterAuthorizer {
  public static allowAccess(): Promise<void> {
    return Promise.resolve()
  }

  public static denyAccess(): Promise<void> {
    return Promise.reject(new NotAuthorizedError('Access denied for this resource'))
  }

  public static authorizeRoles(authorizedRoles: Array<Class<RoleInterface>>, user?: UserEnvelope): Promise<void> {
    if (user && userHasSomeRole(user, authorizedRoles)) {
      return BoosterAuthorizer.allowAccess()
    }
    return BoosterAuthorizer.denyAccess()
  }

  public static build(
    attributes: CommandRoleAccess | QueryRoleAccess | ReadModelRoleAccess | HealthRoleAccess
  ): CommandAuthorizer | QueryAuthorizer | ReadModelAuthorizer | HealthAuthorizer {
    let authorizer: CommandAuthorizer | QueryAuthorizer | ReadModelAuthorizer | HealthAuthorizer =
      BoosterAuthorizer.denyAccess
    if (attributes.authorize === 'all') {
      authorizer = BoosterAuthorizer.allowAccess
    } else if (Array.isArray(attributes.authorize)) {
      authorizer = BoosterAuthorizer.authorizeRoles.bind(null, attributes.authorize)
    } else if (typeof attributes.authorize === 'function') {
      authorizer = attributes.authorize
    }
    return authorizer
  }
}

function userHasSomeRole(user: UserEnvelope, authorizedRoles: Array<Class<RoleInterface>>): boolean {
  return authorizedRoles.some((roleClass) => user.roles?.includes(roleClass.name))
}
