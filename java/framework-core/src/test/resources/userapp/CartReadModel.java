package com.example.store.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelAction;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.store.common.CartItem;
import com.example.store.common.CheckAccess;
import com.example.store.entities.Cart;
import java.util.List;

@ReadModel(authorize = All.class, before = {CheckAccess.class})
public record CartReadModel(UUID id, List<CartItem> cartItems, int checks) {
  @Projects(entity = Cart.class, joinKey = "id", unProject = "removeCart")
  public static ProjectionResult<CartReadModel> updateWithCart(Cart cart, CartReadModel old) {
    return ProjectionResult.of(new CartReadModel(cart.id(), cart.cartItems(), cart.checks()));
  }

  @SuppressWarnings("unchecked")
  public static ProjectionResult<CartReadModel> removeCart(Cart cart, CartReadModel old) {
    return ReadModelAction.DELETE;
  }
}
