import { Entity, Reduces } from '@boostercloud/framework-core'
import { Money } from '../common/money'
import { Product } from './product'
import { OrderCreated } from '../events/order-created'
import { Address } from '../common/address'
import { UUID } from '@boostercloud/framework-types'
import { Cart } from './cart'

/**
 * We need the unitPrice parameter here to keep a record of the price of the product when this item was sold in case that the original product price changes
 */
export interface OrderItem {
  product: Product
  quantity: number
  unitPrice: Money
}

/**
 * An order object represents a completed order that's ready to be delivered
 */
@Entity
export class Order {
  public constructor(
    readonly id: UUID,
    readonly shippingAddress: Address,
    readonly orderItems: Array<OrderItem>,
    readonly fromCart: Cart
  ) {}

  @Reduces(OrderCreated)
  public static createOrder(event: OrderCreated): Order {
    return event.order
  }
}
