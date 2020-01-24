import { ProductUpdated, ProductUpdateReason } from '../../../events/product-updated'
import { ProductUpdatedV1, ProductUpdatedV2, ProductUpdatedV3, ProductUpdatedV4 } from './schema-versions'
import { Migrates, ToVersion } from '@boostercloud/framework-core'

@Migrates(ProductUpdated)
export class ProductUpdatedMigration {
  @ToVersion(2, { fromSchema: ProductUpdatedV1, toSchema: ProductUpdatedV2 })
  public addReasonField(oldEvent: ProductUpdatedV1): ProductUpdatedV2 {
    // The previous ProductUpdate schema only had one field: "product"
    // Version 2 has now an extra "reason" field so that we know why the product was updated
    const defaultReason = 'CatalogChange'
    return new ProductUpdatedV2(oldEvent.product, defaultReason)
  }

  @ToVersion(3, { fromSchema: ProductUpdatedV2, toSchema: ProductUpdatedV3 })
  public changeReasonFieldToEnum(oldEvent: ProductUpdatedV2): ProductUpdatedV3 {
    // The ProductUpdated.reason field was changed to be an enum instead of a string.
    // We try to convert the string to one of the possible enum values. If not, we just fall back to
    // the default reason 'CatalogChange'
    const defaultReason = ProductUpdateReason.CatalogChange
    let reasonAsEnum: ProductUpdateReason | undefined = ProductUpdateReason[oldEvent.reason as ProductUpdateReason]
    if (!reasonAsEnum) {
      reasonAsEnum = defaultReason
    }

    return new ProductUpdatedV3(oldEvent.product, reasonAsEnum)
  }

  @ToVersion(4, { fromSchema: ProductUpdatedV3, toSchema: ProductUpdatedV4 })
  public expandProductFields(oldEvent: ProductUpdatedV3): ProductUpdatedV4 {
    return new ProductUpdatedV4(
      oldEvent.product.id,
      oldEvent.product.sku,
      oldEvent.product.name,
      oldEvent.product.description,
      oldEvent.product.price,
      oldEvent.product.pictures,
      oldEvent.product.deleted,
      oldEvent.reason
    )
  }
}
