package com.boostercloud.integration.migrations.readmodels.cartreadmodel;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.integration.readmodels.CartReadModel;

/**
 * Schema migrations of {@code CartReadModel} ({@code src/migrations/read-models/CartReadModel/migrations.ts}).
 */
@SchemaMigration(CartReadModel.class)
public class CartReadModelMigration {

  /**
   * Adds {@code checks = 0} (the method name is the TypeScript one, copied from another migration).
   *
   * @param old a version 1 read model
   * @return the version 2 read model
   */
  @ToVersion(toVersion = 2, fromSchema = CartReadModelV1.class, toSchema = CartReadModel.class)
  public CartReadModel splitDescriptionFieldIntoShortAndLong(CartReadModelV1 old) {
    return new CartReadModel(old.id(), old.cartItems(), 0, old.shippingAddress(), old.payment(), old.cartItemsIds());
  }
}
