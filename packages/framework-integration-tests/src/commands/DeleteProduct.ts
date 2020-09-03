import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { ProductDeleted } from '../events/ProductDeleted'
import { Admin, SuperUser } from '../roles'

@Command({
  authorize: [Admin, SuperUser],
})
export class DeleteProduct {
  public constructor(readonly productId: UUID) {}

  public static async handle(command: DeleteProduct, register: Register): Promise<void> {
    register.events(new ProductDeleted(command.productId))
  }
}
