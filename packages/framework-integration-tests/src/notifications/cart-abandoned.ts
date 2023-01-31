import { Notification } from '@boostercloud/framework-core'

@Notification()
export class CartAbandoned {
  public constructor(readonly something: string) {}
}
