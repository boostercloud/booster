import { Command } from '@boostercloud/framework-core'
import { Register } from '@boostercloud/framework-types'
import { ProductUpdated, ProductUpdateReason } from '../events/ProductUpdated'
import { SKU } from '../common/sku'
import { Money } from '../common/money'
import { UUID } from '@boostercloud/framework-types'
import { Picture } from '../common/picture'

@Command({
  authorize: 'all',
})
export class UpdateProduct {
  public constructor(
    readonly id: UUID,
    readonly sku: SKU,
    readonly name: string,
    readonly shortDescription: string,
    readonly longDescription: string,
    readonly price: Money,
    readonly pictures: Array<Picture>,
    readonly deleted: boolean = false,
    readonly reason: ProductUpdateReason = ProductUpdateReason.CatalogChange
  ) {}

  public async handle(register: Register): Promise<void> {
    register.events(
      new ProductUpdated(
        this.id,
        this.sku,
        this.name,
        `${this.shortDescription}\n${this.longDescription}`,
        this.price,
        this.pictures,
        this.deleted,
        this.reason
      )
    )
  }
}
