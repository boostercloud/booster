import { UUID } from '@boostercloud/framework-types'

export class CartItem {
  public constructor(public productId: UUID, public quantity: number) {}
}
