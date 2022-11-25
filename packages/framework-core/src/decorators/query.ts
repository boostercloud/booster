/* eslint-disable @typescript-eslint/ban-types */
import { Booster } from '../booster'
import { QueryAuthorizer, QueryInterface, QueryRoleAccess } from '@boostercloud/framework-types'
import { getClassMetadata } from './metadata'
import { BoosterAuthorizer } from '../booster-authorizer'

export function Query(attributes: QueryRoleAccess): <TCommand>(queryClass: QueryInterface<TCommand>) => void {
  return (queryClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.queryHandlers[queryClass.name]) {
        throw new Error(`A query called ${queryClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      const metadata = getClassMetadata(queryClass)
      config.queryHandlers[queryClass.name] = {
        class: queryClass,
        authorizer: BoosterAuthorizer.build(attributes) as QueryAuthorizer,
        properties: metadata.fields,
        methods: metadata.methods,
      }
    })
  }
}
