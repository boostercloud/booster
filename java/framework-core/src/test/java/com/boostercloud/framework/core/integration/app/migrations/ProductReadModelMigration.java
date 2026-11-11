package com.boostercloud.framework.core.integration.app.migrations;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModel;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReadModelV1;

/** Upgrades product read models stored without a category. */
@SchemaMigration(ProductReadModel.class)
public class ProductReadModelMigration {
  /**
   * @param old a version 1 read model
   * @return the version 2 read model
   */
  @ToVersion(toVersion = 2, fromSchema = ProductReadModelV1.class, toSchema = ProductReadModel.class)
  public ProductReadModel addCategory(ProductReadModelV1 old) {
    return new ProductReadModel(old.id(), old.sku(), old.displayName(), old.price(), "legacy");
  }
}
