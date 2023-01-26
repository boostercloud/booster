import { AnyClass } from '@boostercloud/framework-types'
import { Booster } from '../booster'

export type NotificationOptions = {
  topic: string
}

/**
 * Annotation to tell Booster which classes are your Notifications
 * @param eventClass
 * @constructor
 */
export const Notification =
  (options?: NotificationOptions) =>
  (eventClass: AnyClass): void => {
    Booster.configureCurrentEnv((config): void => {
      if (config.events[eventClass.name] || config.events[eventClass.name]) {
        throw new Error(`A notification called ${eventClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }
      if (options?.topic) {
        config.eventToTopic[eventClass.name] = options.topic
        config.topicToEvent[options.topic] = eventClass.name
      }
      config.notifications[eventClass.name] = {
        class: eventClass,
      }
      config.events[eventClass.name] = {
        class: eventClass,
      }
    })
  }
