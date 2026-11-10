package com.boostercloud.integration.queries;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.entities.Cart;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The number of units in a cart, read from the entity ({@code src/queries/cart-total-quantity.ts}). The non-exposed
 * {@code multiply} is set by the before hook: {@value Constants#BEFORE_HOOK_QUERY_MULTIPLY} for
 * {@link Constants#BEFORE_HOOK_QUERY_ID}, 1 otherwise.
 *
 * @param cartId the cart
 * @param multiply the multiplier (non-exposed)
 */
@Query(authorize = All.class, before = CartTotalQuantity.BeforeFn.class)
public record CartTotalQuantity(UUID cartId, @NonExposed double multiply) {

  /**
   * @param input the raw GraphQL input
   * @param currentUser the user, or {@code null}
   * @return the input with {@code multiply}
   */
  public static Map<String, Object> beforeFn(Map<String, Object> input, UserEnvelope currentUser) {
    Map<String, Object> result = new LinkedHashMap<>(input);
    Object cartId = result.get("cartId");
    if (cartId != null && Constants.BEFORE_HOOK_QUERY_ID.equals(cartId.toString())) {
      result.put("multiply", Constants.BEFORE_HOOK_QUERY_MULTIPLY);
      return result;
    }
    result.put("multiply", 1);
    return result;
  }

  /**
   * @param query the query
   * @param queryInfo the request information
   * @return the sum of the quantities times {@code multiply} ({@code 0} for an unknown or empty cart)
   * @throws IllegalStateException for {@link Constants#QUERY_HANDLER_ERROR_CART_ID}
   */
  public static double handle(CartTotalQuantity query, QueryInfo queryInfo) {
    if (query.cartId() != null && Constants.QUERY_HANDLER_ERROR_CART_ID.equals(query.cartId().toString())) {
      throw new IllegalStateException(Constants.QUERY_HANDLER_ERROR_CART_MESSAGE);
    }
    Cart cart = Booster.entity(Cart.class, query.cartId()).orElse(null);
    if (cart == null || cart.cartItems() == null || cart.cartItems().isEmpty()) {
      return 0;
    }
    double accumulator = 0;
    for (CartItem cartItem : cart.cartItems()) {
      accumulator = accumulator + cartItem.quantity() * query.multiply();
    }
    return accumulator;
  }

  /** {@code before: [CartTotalQuantity.beforeFn]}. */
  public static final class BeforeFn implements QueryBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      return beforeFn(input, currentUser);
    }
  }
}
