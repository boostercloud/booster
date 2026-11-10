package com.boostercloud.integration.migrations.commands.updateproduct;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.integration.commands.UpdateProduct;

/**
 * Schema migrations of the {@code UpdateProduct} command ({@code src/migrations/commands/UpdateProduct/migrations.ts}).
 */
@SchemaMigration(UpdateProduct.class)
public class UpdateProductMigration {

  /**
   * The old description becomes the short description; the long one is empty.
   *
   * @param oldEntity a version 1 command
   * @return the version 2 command
   */
  @ToVersion(toVersion = 2, fromSchema = UpdateProductV1.class, toSchema = UpdateProduct.class)
  public UpdateProduct splitDescriptionFieldIntoShortAndLong(UpdateProductV1 oldEntity) {
    return new UpdateProduct(oldEntity.id(), oldEntity.sku(), oldEntity.name(), oldEntity.description(), "",
        oldEntity.price(), oldEntity.pictures(), oldEntity.deleted(), oldEntity.reason(), null, null);
  }
}
