import { Command, generateUUID } from '@boostercloud/framework-core'
import { Product } from '../entities/product'
import { ProductCreated } from '../events/product-created'
import { Register } from '@boostercloud/framework-types'
import { User } from '../roles'

@Command({
  authorize: [User],
})
export class CreateProduct {
  public constructor(readonly product: Product) {}

  public handle(register: Register): void {
    this.product.id = generateUUID()
    register.events(new ProductCreated(this.product))
  }
}
