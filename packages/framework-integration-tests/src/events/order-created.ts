import { Event } from '@boostercloud/framework-core'
import { Order } from '../entities/order'
import { UUID } from '@boostercloud/framework-types'

@Event
export class OrderCreated {
  public constructor(readonly order: Order) {}

  public entityID(): UUID {
    return this.order.id
  }
}
