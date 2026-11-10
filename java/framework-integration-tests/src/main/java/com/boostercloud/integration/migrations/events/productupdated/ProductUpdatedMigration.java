package com.boostercloud.integration.migrations.events.productupdated;

import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.integration.events.ProductUpdateReason;
import com.boostercloud.integration.events.ProductUpdated;
import java.util.LinkedHashMap;

/**
 * Schema migrations of the {@code ProductUpdated} event ({@code src/migrations/events/ProductUpdated/migrations.ts}):
 * 1 → 2 → 3 → 4. The current version (TypeScript's {@code ProductUpdatedV4 extends ProductUpdated}) is
 * {@code ProductUpdated} itself, since records cannot be extended.
 */
@SchemaMigration(ProductUpdated.class)
public class ProductUpdatedMigration {

  /**
   * The previous schema only had the product; version 2 adds why it was updated.
   *
   * @param oldEvent a version 1 event
   * @return the version 2 event, with reason {@code CatalogChange}
   */
  @ToVersion(toVersion = 2, fromSchema = ProductUpdatedV1.class, toSchema = ProductUpdatedV2.class)
  public ProductUpdatedV2 addReasonField(ProductUpdatedV1 oldEvent) {
    String defaultReason = "CatalogChange";
    return new ProductUpdatedV2(oldEvent.product(), defaultReason);
  }

  /**
   * The reason becomes an enum: {@code ProductUpdateReason[reason]} (a lookup by constant name), falling back to
   * {@code CatalogChange}.
   *
   * @param oldEvent a version 2 event
   * @return the version 3 event
   */
  @ToVersion(toVersion = 3, fromSchema = ProductUpdatedV2.class, toSchema = ProductUpdatedV3.class)
  public ProductUpdatedV3 changeReasonFieldToEnum(ProductUpdatedV2 oldEvent) {
    ProductUpdateReason defaultReason = ProductUpdateReason.CatalogChange;
    ProductUpdateReason reasonAsEnum = null;
    for (ProductUpdateReason value : ProductUpdateReason.values()) {
      if (value.name().equals(oldEvent.reason())) {
        reasonAsEnum = value;
      }
    }
    if (reasonAsEnum == null) {
      reasonAsEnum = defaultReason;
    }
    return new ProductUpdatedV3(oldEvent.product(), reasonAsEnum);
  }

  /**
   * The product's fields become the event's; no details and type {@code Other} (the constructor defaults).
   *
   * @param oldEvent a version 3 event
   * @return the version 4 (current) event
   */
  @ToVersion(toVersion = 4, fromSchema = ProductUpdatedV3.class, toSchema = ProductUpdated.class)
  public ProductUpdated expandProductFields(ProductUpdatedV3 oldEvent) {
    Product product = oldEvent.product();
    return new ProductUpdated(product.id(), product.sku(), product.name(), product.description(), product.price(),
        product.pictures(), product.deleted(), oldEvent.reason(), new LinkedHashMap<>(), null);
  }
}
