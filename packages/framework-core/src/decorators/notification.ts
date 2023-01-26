import { Class, EventInterface, NotificationInterface } from '@boostercloud/framework-types'
import { Booster } from '../booster'

/**
 * Annotation to tell Booster which classes are your Notifications
 * @param eventClass
 * @constructor
 */
export function Notification<TEvent extends EventInterface & NotificationInterface>(eventClass: Class<TEvent>): void {
  Booster.configureCurrentEnv((config): void => {
    if (config.events[eventClass.name] || config.events[eventClass.name]) {
      throw new Error(`A notification called ${eventClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
    }
    config.notifications[eventClass.name] = {
      class: eventClass,
    }
    config.events[eventClass.name] = {
      class: eventClass,
    }
  })
}
