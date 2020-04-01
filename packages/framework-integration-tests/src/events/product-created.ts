import { Event } from '@boostercloud/framework-core'
import { Product } from '../entities/product'
import { UUID } from '@boostercloud/framework-types'

@Event
export class ProductCreated {
  public constructor(readonly product: Product) {}

  public entityID(): UUID {
    return this.product.id
  }
}
