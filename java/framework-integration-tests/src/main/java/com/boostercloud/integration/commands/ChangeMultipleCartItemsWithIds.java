package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CartItemChanged;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * {@link ChangeMultipleCartItems} for several carts at once ({@code src/commands/change-multiple-cart-items-with-ids.ts}).
 * Each item is a TypeScript interface ({@code Items { cartId: UUID; itemsCount: number }}), so GraphQL sees a list of
 * {@code JSON} values ({@code [JSON!]!}); Java reads each one as a raw map.
 *
 * @param items the carts and their product counts
 */
@Command(authorize = All.class)
public record ChangeMultipleCartItemsWithIds(List<Map<String, Object>> items) {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(ChangeMultipleCartItemsWithIds command, Register register) {
    double quantity = 1;
    Object[] events = command.items().stream()
        .flatMap(item -> {
          UUID cartId = UUID.of(String.valueOf(item.get("cartId")));
          int itemsCount = ((Number) item.get("itemsCount")).intValue();
          return IntStream.range(0, itemsCount)
              .mapToObj(id -> new CartItemChanged(cartId, UUID.of(String.valueOf(id)), quantity));
        })
        .toArray();
    register.events(events);
  }
}
