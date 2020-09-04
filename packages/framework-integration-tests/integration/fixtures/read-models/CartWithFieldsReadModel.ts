import { ReadModel } from '@boostercloud/framework-core'
import { UUID } from '@boostercloud/framework-types'
import { CartItem } from "../common/CartItem";

@ReadModel({
  authorize: // Specify authorized roles here. Use 'all' to authorize anyone
})
export class CartWithFieldsReadModel {
  public constructor(
    public id: UUID,
    readonly items: Array<CartItem>,
  ) {}

}
