import { Notification, partitionKey } from '@boostercloud/framework-core'

@Notification()
export class CartAbandoned {
  public constructor(@partitionKey readonly something: string) {}
}
