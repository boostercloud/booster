package com.boostercloud.framework.core.integration.app.queries;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.core.integration.app.common.CartItem;
import com.boostercloud.framework.core.integration.app.entities.Cart;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * The number of units of a cart, read from the entity; the hidden multiplier is set by a before hook.
 *
 * @param cartId the cart
 * @param multiplier set by {@link AdminMultiplier}, not part of the GraphQL input
 */
@Query(authorize = All.class, before = AdminMultiplier.class)
public record CartTotalQuantity(UUID cartId, @NonExposed Integer multiplier) {
  /**
   * @param query the query
   * @param queryInfo the request information
   * @return the total
   */
  public static int handle(CartTotalQuantity query, QueryInfo queryInfo) {
    int total = Booster.entity(Cart.class, query.cartId())
        .map(cart -> cart.items().stream().mapToInt(CartItem::quantity).sum())
        .orElse(0);
    return total * (query.multiplier() == null ? 1 : query.multiplier());
  }
}
