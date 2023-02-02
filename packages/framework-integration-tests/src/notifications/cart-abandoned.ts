import { Notification } from '@boostercloud/framework-core'

@Notification({ partitionKey: 'something' })
export class CartAbandoned {
  public constructor(readonly something: string) {}
}
