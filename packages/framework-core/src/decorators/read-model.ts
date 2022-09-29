import {
  Class,
  ReadModelAuthorizer,
  ReadModelFilterHooks,
  ReadModelInterface,
  ReadModelRoleAccess,
} from '@boostercloud/framework-types'
import { Booster } from '../booster'
import { BoosterAuthorizer } from '../booster-authorizer'
import { getClassMetadata } from './metadata'

/**
 * Decorator to register a class as a ReadModel
 * @param attributes
 */
export function ReadModel(
  attributes: ReadModelRoleAccess & ReadModelFilterHooks
): (readModelClass: Class<ReadModelInterface>) => void {
  return (readModelClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.readModels[readModelClass.name]) {
        throw new Error(`A read model called ${readModelClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      let authorizer: ReadModelAuthorizer = BoosterAuthorizer.denyAccess
      if (attributes.authorize === 'all') {
        authorizer = BoosterAuthorizer.allowAccess
      } else if (Array.isArray(attributes.authorize)) {
        authorizer = BoosterAuthorizer.authorizeRoles.bind(undefined, attributes.authorize)
      } else if (typeof attributes.authorize === 'function') {
        authorizer = attributes.authorize
      }

      config.readModels[readModelClass.name] = {
        class: readModelClass,
        properties: getClassMetadata(readModelClass).fields,
        authorizer,
        before: attributes.before ?? [],
      }
    })
  }
}
