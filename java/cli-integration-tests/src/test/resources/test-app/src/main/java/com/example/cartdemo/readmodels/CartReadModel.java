package com.example.cartdemo.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.cartdemo.common.CartItem;
import com.example.cartdemo.entities.Cart;
import java.util.List;

/** The carts, as shown to the users. */
@ReadModel(authorize = All.class)
public record CartReadModel(UUID id, List<CartItem> cartItems) {
  @Projects(entity = Cart.class, joinKey = "id")
  public static ProjectionResult<CartReadModel> updateWithCart(Cart cart, CartReadModel oldCartReadModel) {
    return ProjectionResult.of(new CartReadModel(cart.id(), cart.cartItems()));
  }
}
