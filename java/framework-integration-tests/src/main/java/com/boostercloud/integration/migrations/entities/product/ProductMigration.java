package com.boostercloud.integration.migrations.entities.product;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.integration.entities.Product;
import com.boostercloud.integration.entities.ProductType;
import java.util.LinkedHashMap;

/** Schema migrations of the {@code Product} entity ({@code src/migrations/entities/Product/migrations.ts}). */
@SchemaMigration(Product.class)
public class ProductMigration {

  /**
   * {@code name} becomes {@code displayName}; availability 0, no details and type {@code Other} (the constructor
   * defaults of TypeScript's {@code ProductV2}).
   *
   * @param old a version 1 product
   * @return the version 2 product
   */
  @ToVersion(toVersion = 2, fromSchema = ProductV1.class, toSchema = Product.class)
  public Product changeNameFieldToDisplayName(ProductV1 old) {
    return new Product(old.id(), old.sku(), old.name(), old.description(), old.price(), old.pictures(),
        old.deleted(), 0, new LinkedHashMap<>(), ProductType.OTHER);
  }
}
