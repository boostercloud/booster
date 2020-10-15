import { Command } from '@boostercloud/framework-core'
import { ProductCreated } from '../events/product-created'
import { Register, UUID } from '@boostercloud/framework-types'
import { UserWithEmail, Admin } from '../roles'
import { SKU } from '../common/sku'

@Command({
  authorize: [Admin, UserWithEmail],
})
export class CreateProduct {
  public constructor(
    readonly sku: SKU,
    readonly displayName: string,
    readonly description: string,
    readonly priceInCents: number,
    readonly currency: string
  ) {}

  public static async handle(command: CreateProduct, register: Register): Promise<void> {
    register.events(
      new ProductCreated(UUID.generate(), command.sku, command.displayName, command.description, {
        cents: command.priceInCents,
        currency: command.currency,
      })
    )
  }
}
