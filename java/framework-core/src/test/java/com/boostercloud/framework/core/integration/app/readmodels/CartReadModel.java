package com.boostercloud.framework.core.integration.app.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.CalculatedField;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.core.integration.app.common.CartItem;
import com.boostercloud.framework.core.integration.app.entities.Cart;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;

/**
 * The public view of a cart, with a calculated total and a property hidden from the API.
 *
 * @param id the cart
 * @param items its lines
 * @param checks how many times it was checked
 * @param internalNote a note never exposed through GraphQL
 */
@ReadModel(authorize = All.class, before = CartReadModelGuard.class)
public record CartReadModel(UUID id, List<CartItem> items, int checks, @NonExposed String internalNote) {

  /**
   * @return the number of units in the cart
   */
  @CalculatedField(dependsOn = {"items"})
  public int totalQuantity() {
    return items == null ? 0 : items.stream().mapToInt(CartItem::quantity).sum();
  }

  /**
   * @param cart the cart
   * @param current the current read model, or {@code null}
   * @return the new read model
   */
  @Projects(entity = Cart.class, joinKey = "id", unProject = "removeCart")
  public static ProjectionResult<CartReadModel> project(Cart cart, CartReadModel current) {
    return ProjectionResult.of(new CartReadModel(cart.id(), cart.items(), cart.checks(),
        "projected " + (current == null ? 1 : 2)));
  }

  /**
   * @param cart the cart at the deleted event
   * @param current the current read model
   * @return delete it
   */
  public static ProjectionResult<CartReadModel> removeCart(Cart cart, CartReadModel current) {
    return ProjectionResult.delete();
  }
}
