package com.boostercloud.integration.events;

import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.entities.ProductType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A product was created ({@code src/events/product-created.ts}). The TypeScript default parameters
 * ({@code productDetails = {}}, {@code productType = 'Other'}) apply when the value is missing ({@code null}).
 *
 * @param productId the product
 * @param sku its SKU
 * @param displayName its name
 * @param description its description
 * @param price its price
 * @param productDetails free-form details
 * @param productType one of {@link ProductType}
 */
@Event
public record ProductCreated(UUID productId, String sku, String displayName, String description, Money price,
    Map<String, Object> productDetails, Object productType) {

  /** Applies the defaults. */
  public ProductCreated {
    productDetails = productDetails == null ? new LinkedHashMap<>() : productDetails;
    productType = ProductType.orDefault(productType);
  }

  /**
   * @return the product
   */
  public UUID entityID() {
    return productId;
  }
}
