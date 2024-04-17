import { Class, NotificationInterface } from '@boostercloud/framework-types'
import { Booster } from '../booster'
import { getFunctionArguments } from './metadata'

export type NotificationOptions = {
  topic?: string
}

/**
 * Annotation to tell Booster which classes are your Notifications
 * @param eventClass
 * @constructor
 */
export const Notification =
  <TEvent extends NotificationInterface>(options?: NotificationOptions) =>
  (eventClass: Class<TEvent>): void => {
    Booster.configureCurrentEnv((config): void => {
      if (config.notifications[eventClass.name] || config.events[eventClass.name]) {
        throw new Error(`A notification called ${eventClass.name} is already registered.
        If you think that this is an error, try performing a clean build.`)
      }
      const topic = options?.topic ?? 'defaultTopic'
      if (topic) {
        config.eventToTopic[eventClass.name] = topic
        config.topicToEvent[topic] = eventClass.name
      }
      config.notifications[eventClass.name] = {
        class: eventClass,
      }
    })
  }

/**
 * Optional decorator that marks a field in a notification class as the partition key.
 * This is useful if you want to guarantee that all the event handlers for notifications
 * of the same type will be executed in the same order.
 */
export function partitionKey(
  notificationClass: Class<NotificationInterface>,
  _: string | undefined,
  parameterIndex: number
): void {
  const args = getFunctionArguments(notificationClass)
  const propertyName = args[parameterIndex]
  Booster.configureCurrentEnv((config): void => {
    if (config.partitionKeys[notificationClass.name] && config.partitionKeys[notificationClass.name] !== propertyName) {
      throw new Error(
        `Error trying to register a partition key named \`${propertyName}\` for class \`${
          notificationClass.name
        }\`. It already had the partition key \`${
          config.partitionKeys[notificationClass.name]
        }\` defined and only one partition key is allowed for each notification event.`
      )
    } else {
      config.partitionKeys[notificationClass.name] = propertyName
    }
  })
}
