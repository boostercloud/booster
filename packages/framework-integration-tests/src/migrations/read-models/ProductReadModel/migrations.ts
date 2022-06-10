import { ProductReadModelV1, ProductReadModelV2 } from './schema-versions'
import { Migrates, ToVersion } from '@boostercloud/framework-core'
import { ProductReadModel } from '../../../read-models/product-read-model'

@Migrates(ProductReadModel)
export class ProductReadModelMigration {
  @ToVersion(2, { fromSchema: ProductReadModelV1, toSchema: ProductReadModelV2 })
  public async splitDescriptionFieldIntoShortAndLong(old: ProductReadModelV1): Promise<ProductReadModelV2> {
    return new ProductReadModelV2(
      old.id,
      old.sku,
      old.name, // Assign the old "name" field to new "displayName" field
      old.description,
      1000,
      old.deleted,
      old.price,
      []
    )
  }
}
