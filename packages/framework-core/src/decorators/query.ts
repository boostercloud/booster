/* eslint-disable @typescript-eslint/ban-types */
import { Booster } from '../booster'
import { QueryInterface, QueryFilterHooks, QueryRoleAccess, QueryAuthorizer } from '@boostercloud/framework-types'
import { getClassMetadata } from './metadata'
import { BoosterAuthorizer } from '../booster-authorizer'

/**
 * Annotation to tell Booster which classes are your entities
 * @param attributes
 * @constructor
 */
export function Query(
  attributes: QueryRoleAccess & QueryFilterHooks
): <TQuery>(queryClass: QueryInterface<TQuery>) => void {
  return (queryClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.queryHandlers[queryClass.name]) {
        throw new Error(`A query called ${queryClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      let authorizer: QueryAuthorizer = BoosterAuthorizer.denyAccess
      if (attributes.authorize === 'all') {
        authorizer = BoosterAuthorizer.allowAccess
      } else if (Array.isArray(attributes.authorize)) {
        authorizer = BoosterAuthorizer.authorizeRoles.bind(null, attributes.authorize)
      } else if (typeof attributes.authorize === 'function') {
        authorizer = attributes.authorize
      }

      const metadata = getClassMetadata(queryClass)
      config.queryHandlers[queryClass.name] = {
        class: queryClass,
        authorizer,
        before: attributes.before ?? [],
        properties: metadata.fields,
        methods: metadata.methods,
      }
    })
  }
}
