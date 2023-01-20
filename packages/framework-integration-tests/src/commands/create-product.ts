import { Command } from '@boostercloud/framework-core'
import { ProductCreated } from '../events/product-created'
import { Register, UUID } from '@boostercloud/framework-types'
import { UserWithEmail, Admin } from '../roles'
import { ProductType } from '../entities/product'

@Command({
  authorize: [Admin, UserWithEmail],
})
export class CreateProduct {
  public constructor(
    readonly sku: string,
    readonly displayName: string,
    readonly description: string,
    readonly priceInCents: number,
    readonly currency: string,
    readonly productID?: UUID,
    readonly productDetails?: Record<string, unknown>,
    readonly productType?: ProductType
  ) {}

  public static async handle(command: CreateProduct, register: Register): Promise<void> {
    const productID = command.productID ?? UUID.generate()
    register.events(
      new ProductCreated(
        productID,
        command.sku,
        command.displayName,
        command.description,
        {
          cents: command.priceInCents,
          currency: command.currency,
        },
        command.productDetails,
        command.productType
      )
    )
  }
}
