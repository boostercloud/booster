package com.boostercloud.integration.migrations.readmodels.productreadmodel;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.integration.entities.ProductType;
import com.boostercloud.integration.readmodels.ProductReadModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Schema migrations of {@code ProductReadModel} ({@code src/migrations/read-models/ProductReadModel/migrations.ts}).
 */
@SchemaMigration(ProductReadModel.class)
public class ProductReadModelMigration {

  /**
   * {@code name} becomes {@code displayName}, availability 1000, no details, type {@code Other}, no packs; the
   * pictures are dropped (the method name is the TypeScript one, copied from another migration).
   *
   * @param old a version 1 read model
   * @return the version 2 read model
   */
  @ToVersion(toVersion = 2, fromSchema = ProductReadModelV1.class, toSchema = ProductReadModel.class)
  public ProductReadModel splitDescriptionFieldIntoShortAndLong(ProductReadModelV1 old) {
    return new ProductReadModel(old.id(), old.sku(), old.name(), old.description(), 1000, old.deleted(),
        new LinkedHashMap<>(), ProductType.OTHER, old.price(), new ArrayList<>());
  }
}
