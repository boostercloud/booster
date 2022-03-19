import { Command } from '@boostercloud/framework-core'
import { CommandInput, Register, UUID } from '@boostercloud/framework-types'
import { ProductDeleted } from '../events/product-deleted'
import { ProductAvailabilityChanged } from '../events/product-availability-changed'

@Command({
  authorize: 'all',
  after: [DeleteProduct.afterFn],
})
export class DeleteProduct {
  public constructor(readonly productId: UUID, readonly deleteReason: string) {}

  public static async handle(command: DeleteProduct, register: Register): Promise<void> {
    register.events(new ProductDeleted(command.productId))
  }

  public static async afterFn(result: unknown, command: CommandInput, register: Register): Promise<void> {
    const productId = (command as DeleteProduct).productId
    register.events(new ProductAvailabilityChanged(productId, 0))
  }
}
