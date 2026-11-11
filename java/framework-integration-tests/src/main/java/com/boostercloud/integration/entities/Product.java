package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.common.Picture;
import com.boostercloud.integration.events.ProductAvailabilityChanged;
import com.boostercloud.integration.events.ProductCreated;
import com.boostercloud.integration.events.ProductDeleted;
import com.boostercloud.integration.events.ProductUpdated;
import com.boostercloud.integration.roles.UserWithEmail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A product ({@code src/entities/product.ts}); schema version 2 (see {@code migrations.entities.product}). Only users
 * with the {@code UserWithEmail} role can read its events. TypeScript's defaults ({@code deleted = false},
 * {@code availability = 0}, {@code productDetails = {}}, {@code productType = 'Other'}) apply to missing values.
 *
 * @param id the product
 * @param sku its SKU
 * @param displayName its name
 * @param description its description
 * @param price its price
 * @param pictures its pictures
 * @param deleted whether it was deleted
 * @param availability units available
 * @param productDetails free-form details
 * @param productType one of {@link ProductType}
 */
@Entity(authorizeReadEvents = UserWithEmail.class)
public record Product(UUID id, String sku, String displayName, String description, Money price,
    List<Picture> pictures, boolean deleted, double availability, Map<String, Object> productDetails,
    Object productType) {

  /** Applies the defaults. */
  public Product {
    pictures = pictures == null ? new ArrayList<>() : pictures;
    productDetails = productDetails == null ? new LinkedHashMap<>() : productDetails;
    productType = ProductType.orDefault(productType);
  }

  /**
   * @return the product id (called to check handlers receive real instances, TypeScript issue #849)
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @return the new product, available 0, not deleted, without pictures
   */
  @Reduces(ProductCreated.class)
  public static Product create(ProductCreated event) {
    return new Product(event.productId(), event.sku(), event.displayName(), event.description(), event.price(),
        new ArrayList<>(), false, 0, event.productDetails(), event.productType());
  }

  /**
   * @param event the event
   * @return the updated product; the availability is reset to 0, as in TypeScript
   */
  @Reduces(ProductUpdated.class)
  public static Product update(ProductUpdated event) {
    return new Product(event.id(), event.sku(), event.name(), event.description(), event.price(), event.pictures(),
        event.deleted(), 0, event.productDetails(), event.productType());
  }

  /**
   * @param event the event
   * @param currentProduct the current product, or {@code null}
   * @return the product marked as deleted ({@code null} when there is none)
   */
  @Reduces(ProductDeleted.class)
  public static Product delete(ProductDeleted event, Product currentProduct) {
    if (currentProduct == null) {
      return null;
    }
    return new Product(currentProduct.id(), currentProduct.sku(), currentProduct.displayName(),
        currentProduct.description(), currentProduct.price(), currentProduct.pictures(), true,
        currentProduct.availability(), currentProduct.productDetails(), currentProduct.productType());
  }

  /**
   * @param event the event
   * @param currentProduct the current product, or {@code null}
   * @return the product with the availability changed ({@code null} when there is none)
   */
  @Reduces(ProductAvailabilityChanged.class)
  public static Product changeAvailability(ProductAvailabilityChanged event, Product currentProduct) {
    if (currentProduct == null) {
      return null;
    }
    return new Product(currentProduct.id(), currentProduct.sku(), currentProduct.displayName(),
        currentProduct.description(), currentProduct.price(), currentProduct.pictures(), currentProduct.deleted(),
        currentProduct.availability() + event.quantity(), currentProduct.productDetails(),
        currentProduct.productType());
  }
}
