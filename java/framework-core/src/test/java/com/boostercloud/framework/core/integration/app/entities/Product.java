package com.boostercloud.framework.core.integration.app.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.core.integration.app.events.ProductCreated;
import com.boostercloud.framework.core.integration.app.events.ProductDeleted;
import com.boostercloud.framework.core.integration.app.events.ProductUpdated;
import com.boostercloud.framework.core.integration.app.roles.Admin;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A product of the catalogue; only admins can read its events.
 *
 * @param id the product
 * @param sku the stock keeping unit
 * @param displayName the name shown to customers
 * @param price the price
 * @param category the category
 * @param deleted whether it was withdrawn
 */
@Entity(authorizeReadEvents = Admin.class)
public record Product(UUID id, String sku, String displayName, double price, String category, boolean deleted) {

  /**
   * @param event the creation
   * @param current ignored
   * @return the product
   */
  @Reduces(ProductCreated.class)
  public static Product created(ProductCreated event, Product current) {
    return new Product(event.productId(), event.sku(), event.displayName(), event.price(), event.category(), false);
  }

  /**
   * @param event the change
   * @param current the product
   * @return the new product
   */
  @Reduces(ProductUpdated.class)
  public static Product updated(ProductUpdated event, Product current) {
    return new Product(current.id(), current.sku(), event.displayName(), event.price(), current.category(),
        current.deleted());
  }

  /**
   * @param event the withdrawal
   * @param current the product
   * @return the withdrawn product
   */
  @Reduces(ProductDeleted.class)
  public static Product deleted(ProductDeleted event, Product current) {
    return new Product(current.id(), current.sku(), current.displayName(), current.price(), current.category(), true);
  }

  /**
   * @param newDisplayName a name
   * @return this product with that name
   */
  public Product withDisplayName(String newDisplayName) {
    return new Product(id, sku, newDisplayName, price, category, deleted);
  }
}
