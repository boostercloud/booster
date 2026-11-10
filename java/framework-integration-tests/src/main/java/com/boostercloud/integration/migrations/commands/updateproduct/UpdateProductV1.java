package com.boostercloud.integration.migrations.commands.updateproduct;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import com.boostercloud.integration.events.ProductUpdateReason;
import java.util.List;

/**
 * Version 1 of the {@code UpdateProduct} command ({@code src/migrations/commands/UpdateProduct/schema-versions.ts}):
 * one {@code description} instead of a short and a long one. The current version (TypeScript's
 * {@code UpdateProductV2 extends UpdateProduct}) is {@code UpdateProduct} itself, since records cannot be extended.
 *
 * @param id the product
 * @param sku its SKU
 * @param name its name
 * @param description its description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it is deleted ({@code false} by default)
 * @param reason why it changes ({@code CatalogChange} by default)
 */
public record UpdateProductV1(UUID id, String sku, String name, String description, Money price,
    List<Picture> pictures, boolean deleted, ProductUpdateReason reason) {

  /** Applies the defaults. */
  public UpdateProductV1 {
    reason = reason == null ? ProductUpdateReason.CatalogChange : reason;
  }
}
