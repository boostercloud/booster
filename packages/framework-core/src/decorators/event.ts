import { Class, NotificationInterface } from '@boostercloud/framework-types'
import { Booster } from '../booster'

/**
 * Annotation to tell Booster which classes are your Events
 * @param notificationClass
 * @constructor
 */
// Disabling unused vars here, because it won't allow us to call the decorator without parens
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function Notification<TNotification extends NotificationInterface>(
  notificationClass: Class<TNotification>
): void {
  Booster.configureCurrentEnv((config): void => {
    if (config.events[notificationClass.name]) {
      throw new Error(`A notification called ${notificationClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
    }
    config.notifications[notificationClass.name] = {
      class: notificationClass,
    }
  })
}
