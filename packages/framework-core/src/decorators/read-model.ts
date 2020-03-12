import { Class, ReadModelInterface, RoleAccess } from '@boostercloud/framework-types'
import { Booster } from '../booster'

/**
 * Decorator to register a class as a ReadModel
 * @param attributes
 */
export function ReadModel(attributes: RoleAccess): (readModelClass: Class<ReadModelInterface>) => void {
  return (readModelClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.readModels[readModelClass.name]) {
        throw new Error(`A read model called ${readModelClass.name} is already registered.`)
      }

      config.readModels[readModelClass.name] = {
        class: readModelClass,
        authorizedRoles: attributes.authorize,
      }
    })
  }
}
