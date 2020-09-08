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

  public static async handle(command: DeleteProduct, register: Register): Promise<void> {
    await Booster.destroyEntity(Product, command.productId)
    register.events(new ProductDeleted(command.productId))
  }
}
