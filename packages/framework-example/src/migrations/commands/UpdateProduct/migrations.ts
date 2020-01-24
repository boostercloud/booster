import { UpdateProductV1, UpdateProductV2 } from './schema-versions'
import { Migrates, ToVersion } from '@boostercloud/framework-core'
import { UpdateProduct } from '../../../commands/update-product'

@Migrates(UpdateProduct)
export class UpdateProductMigration {
  @ToVersion(2, { fromSchema: UpdateProductV1, toSchema: UpdateProductV2 })
  public splitDescriptionFieldIntoShortAndLong(oldEntity: UpdateProductV1): UpdateProductV2 {
    return new UpdateProductV2(
      oldEntity.id,
      oldEntity.sku,
      oldEntity.name,
      oldEntity.description, // We put the old "description" field in the new "shortDescription" field
      '', // The new "longDescription" defaults to empty
      oldEntity.price,
      oldEntity.pictures,
      oldEntity.deleted,
      oldEntity.reason
    )
  }
}
