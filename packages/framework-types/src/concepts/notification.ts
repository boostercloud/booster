import { Class } from '../typelevel'

/**
 * All Notification classes of your application must implement this interface.
 */
export interface NotificationInterface {
  partitionId?: string
  topic?: string
}

export interface NotificationMetadata {
  readonly class: Class<NotificationInterface>
}
