package com.boostercloud.integration.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.Constants;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The before hook and the failing branch of {@link CartTotalQuantity} ({@code src/queries/cart-total-quantity.ts}). */
class CartTotalQuantityTest {

  @Test
  void theHookMultipliesTheMagicCartByTenAndEveryOtherOneByOne() {
    assertThat(CartTotalQuantity.beforeFn(Map.of("cartId", Constants.BEFORE_HOOK_QUERY_ID), null))
        .isEqualTo(Map.of("cartId", Constants.BEFORE_HOOK_QUERY_ID, "multiply", Constants.BEFORE_HOOK_QUERY_MULTIPLY));
    assertThat(CartTotalQuantity.beforeFn(Map.of("cartId", "cart-1"), null))
        .isEqualTo(Map.of("cartId", "cart-1", "multiply", 1));
    assertThat(Constants.BEFORE_HOOK_QUERY_MULTIPLY).isEqualTo(10);
  }

  @Test
  void theHandlerFailsForTheQueryHandlerErrorCart() {
    assertThatThrownBy(() -> CartTotalQuantity.handle(
        new CartTotalQuantity(UUID.of(Constants.QUERY_HANDLER_ERROR_CART_ID), 1), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(Constants.QUERY_HANDLER_ERROR_CART_MESSAGE);
  }
}
