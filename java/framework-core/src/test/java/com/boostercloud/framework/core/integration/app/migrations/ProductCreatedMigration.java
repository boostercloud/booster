package com.boostercloud.framework.core.integration.app.migrations;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.framework.core.integration.app.events.ProductCreated;
import com.boostercloud.framework.core.integration.app.events.ProductCreatedV1;

/** Upgrades {@code ProductCreated} events stored without a category. */
@SchemaMigration(ProductCreated.class)
public class ProductCreatedMigration {
  /**
   * @param old a version 1 event
   * @return the version 2 event
   */
  @ToVersion(toVersion = 2, fromSchema = ProductCreatedV1.class, toSchema = ProductCreated.class)
  public ProductCreated addCategory(ProductCreatedV1 old) {
    return new ProductCreated(old.productId(), old.sku(), old.displayName(), old.price(), "uncategorized");
  }
}
