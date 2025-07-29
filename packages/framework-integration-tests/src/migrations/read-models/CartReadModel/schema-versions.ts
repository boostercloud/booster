import { UUID } from '@boostercloud/framework-types'
import { CartReadModel } from '../../../read-models/cart-read-model'
import { CartItem } from '../../../common/cart-item'
import { Address } from '../../../common/address'
import { Payment } from '../../../entities/payment'

export class CartReadModelV1 {
  public constructor(
    readonly id: UUID,
    readonly cartItems: Array<CartItem>,
    public shippingAddress?: Address,
    public payment?: Payment,
    public cartItemsIds?: Array<string>
  ) {}
}

// Current version
export class CartReadModelV2 extends CartReadModel {}
