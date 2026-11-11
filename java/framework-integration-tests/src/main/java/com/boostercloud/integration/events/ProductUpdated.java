package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import com.boostercloud.integration.entities.ProductType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A product was updated ({@code src/events/product-updated.ts}); schema version 4 (see
 * {@code migrations.events.productupdated}). {@code deleted} defaults to {@code false} (a primitive),
 * {@code productDetails} to {@code {}} and {@code productType} to {@code 'Other'}.
 *
 * @param id the product
 * @param sku its SKU
 * @param name its name
 * @param description its description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it is deleted
 * @param reason why it changed
 * @param productDetails free-form details
 * @param productType one of {@link ProductType}
 */
@Event
public record ProductUpdated(UUID id, String sku, String name, String description, Money price,
    List<Picture> pictures, boolean deleted, ProductUpdateReason reason, Map<String, Object> productDetails,
    Object productType) {

  /** Applies the defaults. */
  public ProductUpdated {
    productDetails = productDetails == null ? new LinkedHashMap<>() : productDetails;
    productType = ProductType.orDefault(productType);
  }

  /**
   * @return the product
   */
  public UUID entityID() {
    return id;
  }
}
