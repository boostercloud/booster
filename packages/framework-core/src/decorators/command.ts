import { Booster } from '../booster'
import { CommandInterface, CommandFilterHooks, RoleAccess } from '@boostercloud/framework-types'
import { getPropertiesMetadata } from './metadata'

/**
 * Annotation to tell Booster which classes are your entities
 * @param attributes
 * @constructor
 */
export function Command(
  attributes: RoleAccess & CommandFilterHooks
): <TCommand>(commandClass: CommandInterface<TCommand>) => void {
  return (commandClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.commandHandlers[commandClass.name]) {
        throw new Error(`A command called ${commandClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      config.commandHandlers[commandClass.name] = {
        class: commandClass,
        authorizedRoles: attributes.authorize,
        before: attributes.before ?? [],
        properties: getPropertiesMetadata(commandClass),
      }
    })
  }
}
