import { Command } from '@boostercloud/framework-core'
import { Product } from '../entities/product'
import { ProductCreated } from '../events/product-created'
import { Register, UUID } from '@boostercloud/framework-types'
import { User } from '../roles'

@Command({
  authorize: [User],
})
export class CreateProduct {
  public constructor(readonly product: Product) {}

  public handle(register: Register): void {
    this.product.id = UUID.generate()
    register.events(new ProductCreated(this.product))
  }
}
