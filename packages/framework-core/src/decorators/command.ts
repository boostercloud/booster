import { Booster } from '../booster'
import { Class, CommandInterface, RoleAccess } from '@boostercloud/framework-types'

/**
 * Annotation to tell Booster which classes are your entities
 * @param attributes
 * @constructor
 */
export function Command(attributes: RoleAccess): <TCommand extends CommandInterface>(command: Class<TCommand>) => void {
  return (command) => {
    Booster.configure((config): void => {
      config.commandHandlers[command.name] = {
        class: command,
        authorizedRoles: attributes.authorize,
      }
    })
  }
}
