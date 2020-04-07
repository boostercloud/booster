import { Migrates, ToVersion } from '@boostercloud/framework-core'
import { Product } from '../../../entities/product'
import { ProductV1, ProductV2 } from './schema-versions'

@Migrates(Product)
export class ProductMigration {
  @ToVersion(2, { fromSchema: ProductV1, toSchema: ProductV2 })
  public changeNameFieldToDisplayName(old: ProductV1): ProductV2 {
    return new ProductV2(
      old.id,
      old.sku,
      old.name, // Assign the old "name" field to new "displayName" field
      old.description,
      old.price,
      old.pictures,
      old.deleted
    )
  }
}
