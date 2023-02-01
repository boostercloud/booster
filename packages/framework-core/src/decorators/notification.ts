import { Class, NotificationInterface } from '@boostercloud/framework-types'
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
  <TEvent extends NotificationInterface>(eventClass: Class<TEvent>): void => {
    Booster.configureCurrentEnv((config): void => {
      if (config.notifications[eventClass.name] || config.events[eventClass.name]) {
        throw new Error(`A notification called ${eventClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }
      const topic = options?.topic ?? 'default-topic'
      if (topic) {
        config.eventToTopic[eventClass.name] = topic
        config.topicToEvent[topic] = eventClass.name
      }
      config.notifications[eventClass.name] = {
        class: eventClass,
      }
    })
  }
