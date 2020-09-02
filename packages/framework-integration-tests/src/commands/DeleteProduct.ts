import { Command, Booster } from '@boostercloud/framework-core'
import { UUID, Register } from '@boostercloud/framework-types'
import { Admin } from '../roles'
import { Product } from '../entities/Product'
import { ProductDeleted } from '../events/ProductDeleted'

@Command({
  authorize: [Admin],
})
export class DeleteProduct {
  public constructor(readonly productId: UUID) {}

  public async handle(register: Register): Promise<void> {
    await Booster.destroyEntity(Product, this.productId)
    register.events(new ProductDeleted(this.productId))
  }
}
