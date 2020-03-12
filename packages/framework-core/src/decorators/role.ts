import { Booster } from '../booster'
import { Class, RoleMetadata, RoleInterface } from '@boostercloud/framework-types'

/**
 * Annotation to tell Booster which classes represent your roles
 * @param roleMetadata
 */
export function Role(roleMetadata: RoleMetadata): (role: Class<RoleInterface>) => void {
  return (role): void => {
    Booster.configureCurrentEnv((config): void => {
      config.roles[role.name] = roleMetadata
    })
  }
}
