import { Booster } from '../booster'
import { Class, CommandInterface, RoleAccess } from '@boostercloud/framework-types'

/**
 * Annotation to tell Booster which classes are your entities
 * @param attributes
 * @constructor
 */
export function Command(attributes: RoleAccess): (commandClass: Class<CommandInterface>) => void {
  return (commandClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.commandHandlers[commandClass.name]) {
        throw new Error(`A command called ${commandClass.name} is already registered.`)
      }

      config.commandHandlers[commandClass.name] = {
        class: commandClass,
        authorizedRoles: attributes.authorize,
      }
    })
  }
}
