import { Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { ProductUpdated, ProductUpdateReason } from '../events/product-updated'
import { Money } from '../common/money'
import { UUID } from '@boostercloud/framework-types'
import { Picture } from '../common/picture'
import { ProductType } from '../entities/product'

@Command({
  authorize: 'all',
})
export class UpdateProduct {
  public constructor(
    readonly id: UUID,
    readonly sku: string,
    readonly name: string,
    readonly shortDescription: string,
    readonly longDescription: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    readonly deleted: boolean = false,
    readonly reason: ProductUpdateReason = ProductUpdateReason.CatalogChange,
    readonly productDetails?: Record<string, unknown>,
    readonly productType?: ProductType
  ) {}

  public static async handle(command: UpdateProduct, register: Register): Promise<void> {
    register.events(
      new ProductUpdated(
        command.id,
        command.sku,
        command.name,
        `${command.shortDescription}\n${command.longDescription}`,
        command.price,
        command.pictures,
        command.deleted,
        command.reason,
        command.productDetails,
        command.productType
      )
    )
  }
}
