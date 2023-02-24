import { Class } from '../typelevel'

/**
 * All Notification classes of your application must implement this interface.
 */
export type NotificationInterface = {
  partitionId?: string
  topic?: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  [key: string]: any
}

export interface NotificationMetadata {
  readonly class: Class<NotificationInterface>
}
