package com.boostercloud.integration.commands;

import com.boostercloud.framework.common.Promises;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTouchEntityHandler;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.integration.entities.Cart;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Touches the carts of the first page of 500 ids so that their read models are projected again
 * ({@code src/commands/touch-command.ts}). Both {@code Promise.all} steps run concurrently with
 * {@link Promises#runAll}.
 */
@Command(authorize = All.class)
public record TouchCommand() {

  /**
   * @param command the command
   * @param register the register
   */
  public static void handle(TouchCommand command, Register register) {
    PaginatedEntitiesIdsResult entitiesIdsResult = Booster.entitiesIDs("Cart", 500, null);
    List<Callable<Cart>> reads = entitiesIdsResult.items().stream()
        .map(entity -> (Callable<Cart>) () -> Booster.entity(Cart.class, entity.entityID()).orElse(null))
        .toList();
    List<Cart> carts = Promises.runAll(reads);
    if (carts == null || carts.isEmpty()) {
      return;
    }
    List<Callable<UUID>> touches = carts.stream()
        .map(cart -> (Callable<UUID>) () -> {
          Cart validCart = cart;
          BoosterTouchEntityHandler.touchEntity("Cart", validCart.id());
          System.out.println("Touched " + validCart);
          return validCart.id();
        })
        .toList();
    Promises.runAll(touches);
  }
}
