package com.boostercloud.integration.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.entities.Cart;
import com.boostercloud.integration.events.CartItemChanged;
import com.boostercloud.integration.events.StockMoved;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The hooks of {@link AppErrorHandler} ({@code src/common/app-error-handler.ts}): which errors are replaced, kept or
 * swallowed. The exact metadata JSON in the messages is pinned by {@code ApplicationRegistrationTest}.
 */
class AppErrorHandlerTest {

  private static final RuntimeException ERROR = new IllegalStateException("boom");

  private static CommandEnvelope command(String cartId) {
    return CommandEnvelope.builder().typeName("ChangeCartItem").version(1).value(Map.of("cartId", cartId)).build();
  }

  private static EventEnvelope event(String entityId) {
    return EventEnvelope.builder().typeName("CartItemChanged").entityTypeName("Cart").entityID(UUID.of(entityId))
        .version(1).build();
  }

  @Test
  void commandHandlerErrorsAreReplacedKeptOrSwallowedByCartId() {
    assertThat(AppErrorHandler.onCommandHandlerError(ERROR,
        command(Constants.COMMAND_HANDLER_ERROR_IGNORED_CART_ID), null)).isNull();
    assertThat(AppErrorHandler.onCommandHandlerError(ERROR, command(Constants.COMMAND_HANDLER_ERROR_CART_ID), null))
        .hasMessage("boom-onCommandHandlerError with metadata: undefined");
    assertThat(AppErrorHandler.onCommandHandlerError(ERROR,
        command(Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_ID), null))
        .hasMessage("boom-onBeforeCommandHandlerError with metadata: undefined");
    assertThat(AppErrorHandler.onCommandHandlerError(ERROR, command("other"), null)).isSameAs(ERROR);
  }

  @Test
  void queryHandlerErrorsAreReplacedForTheMagicCart() {
    QueryEnvelope magic = QueryEnvelope.builder().typeName("CartTotalQuantity").version(1)
        .value(Map.of("cartId", Constants.QUERY_HANDLER_ERROR_CART_ID)).build();
    QueryEnvelope other = QueryEnvelope.builder().typeName("CartTotalQuantity").version(1)
        .value(Map.of("cartId", "other")).build();

    assertThat(AppErrorHandler.onQueryHandlerError(ERROR, magic)).hasMessage("boom-onQueryHandlerError");
    assertThat(AppErrorHandler.onQueryHandlerError(ERROR, other)).isSameAs(ERROR);
  }

  @Test
  void scheduledCommandErrorsAreAlwaysReplaced() {
    assertThat(AppErrorHandler.onScheduledCommandHandlerError(ERROR, null, null))
        .hasMessage("boom-onScheduledCommandHandlerError with metadata: undefined");
  }

  @Test
  void dispatchReducerAndProjectionErrorsAreReplacedForTheirMagicIds() {
    StockMoved dispatchError = new StockMoved(Constants.DISPATCH_EVENT_ERROR_CART_ID, "provider", "madrid", 1);
    CartItemChanged reducerError =
        new CartItemChanged(UUID.of(Constants.REDUCER_ERROR_CART_ID), UUID.of("p"), 1);
    Cart projectionError = new Cart(UUID.of(Constants.PROJECTION_ERROR_CART_ID), List.of());

    assertThat(AppErrorHandler.onDispatchEventHandlerError(ERROR, null, null, dispatchError))
        .hasMessage("boom-onDispatchEventHandlerError with metadata: undefined");
    assertThat(AppErrorHandler.onDispatchEventHandlerError(ERROR, null, null, null)).isSameAs(ERROR);
    assertThat(AppErrorHandler.onReducerError(ERROR, null, null, reducerError, null))
        .hasMessage("boom-onReducerError with metadata: undefined");
    assertThat(AppErrorHandler.onReducerError(ERROR, null, null, new CartItemChanged(UUID.of("c"), UUID.of("p"), 1),
        null)).isSameAs(ERROR);
    assertThat(AppErrorHandler.onProjectionError(ERROR, null, null, projectionError, null))
        .hasMessage("boom-onProjectionError with metadata: undefined");
    assertThat(AppErrorHandler.onProjectionError(ERROR, null, null, new Cart(UUID.of("c"), List.of()), null))
        .isSameAs(ERROR);
  }

  @Test
  void eventErrorsAreReplacedOrSwallowedByEntityId() {
    assertThat(AppErrorHandler.onEventError(ERROR, event(Constants.EVENT_ERROR_CART_ID)))
        .hasMessage("boom-onEventError");
    assertThat(AppErrorHandler.onEventError(ERROR, event(Constants.IGNORE_EVENT_ERROR_CART_ID))).isNull();
    assertThat(AppErrorHandler.onEventError(ERROR, event("other"))).isSameAs(ERROR);
  }

  @Test
  void onErrorAlwaysAppendsItsSuffix() {
    assertThat(AppErrorHandler.onError(ERROR)).hasMessage("boom-onError");
    assertThat(AppErrorHandler.onError(null)).hasMessage("undefined-onError");
  }
}
