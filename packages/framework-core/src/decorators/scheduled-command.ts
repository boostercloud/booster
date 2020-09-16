import { Booster } from '../booster'
import { ScheduledCommandInterface, ScheduleInterface } from '@boostercloud/framework-types'

/**
 * Annotation to tell Booster which classes are scheduled commands
 * @param attributes
 * @constructor
 */
export function ScheduledCommand(attributes: ScheduleInterface): (scheduledCommandClass: ScheduledCommandInterface) => void {
  return (commandClass) => {
    Booster.configureCurrentEnv((config): void => {
      if (config.scheduledCommandHandlers[commandClass.name]) {
        throw new Error(`A command called ${commandClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }

      config.scheduledCommandHandlers[commandClass.name] = {
        class: commandClass,
        scheduledOn: attributes,
      }
    })
  }
}
