package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.readmodels.CartReadModel;
import java.util.List;
import java.util.Map;

/**
 * Reads the {@code id} and {@code shippingAddress} of a cart from the server side
 * ({@code src/commands/cart-shipping-address.ts}): a list of selected objects, or a {@code {items, count, cursor}}
 * page with {@code paginatedVersion}. The GraphQL result is {@code JSON} ({@code unknown} in TypeScript).
 *
 * @param cartId the cart
 * @param paginatedVersion whether to return a page
 */
@Command(authorize = All.class)
public record CartShippingAddress(UUID cartId, boolean paginatedVersion) {

  /**
   * @param command the command
   * @param register the register
   * @return the search result as is
   */
  public static Object handle(CartShippingAddress command, Register register) {
    return Booster.readModel(CartReadModel.class)
        .filter(Map.of("id", Map.of("eq", command.cartId())))
        .select(List.of("id", "shippingAddress"))
        .paginatedVersion(command.paginatedVersion())
        .searchRaw();
  }
}
