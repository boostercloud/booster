import { Class, ReadModelInterface, RoleAccess } from '@boostercloud/framework-types'
import { BoosterApp } from '../booster-app'
import { getPropertiesMetadata } from './metadata'

/**
 * Decorator to register a class as a ReadModel
 * @param attributes
 */
export function ReadModel(attributes: RoleAccess): (readModelClass: Class<ReadModelInterface>) => void {
  return (readModelClass) => {
    BoosterApp.configureCurrentEnv((config): void => {
      if (config.readModels[readModelClass.name]) {
        throw new Error(`A read model called ${readModelClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      config.readModels[readModelClass.name] = {
        class: readModelClass,
        properties: getPropertiesMetadata(readModelClass),
        authorizedRoles: attributes.authorize,
      }
    })
  }
}
