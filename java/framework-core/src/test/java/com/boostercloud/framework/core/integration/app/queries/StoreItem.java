package com.boostercloud.framework.core.integration.app.queries;

import com.boostercloud.framework.types.concepts.UUID;

/** Something the store search can find: a cart or a product (a GraphQL union). */
public sealed interface StoreItem permits StoreItem.CartView, StoreItem.ProductView {

  /**
   * A cart found by the search.
   *
   * @param cartId the cart
   * @param units its units
   */
  record CartView(UUID cartId, int units) implements StoreItem {}

  /**
   * A product found by the search.
   *
   * @param productId the product
   * @param sku its SKU
   */
  record ProductView(UUID productId, String sku) implements StoreItem {}
}
