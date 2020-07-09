import { Command } from '@boostercloud/framework-core'
import { Register, UUID } from '@boostercloud/framework-types'
import { ProductDeleted } from '../events/ProductDeleted'
import { Admin } from '../roles'

@Command({
  authorize: [Admin],
})
export class DeleteProduct {
  public constructor(readonly productId: UUID) {}

  public async handle(register: Register): Promise<void> {
    register.events(new ProductDeleted(this.productId))
  }
}
