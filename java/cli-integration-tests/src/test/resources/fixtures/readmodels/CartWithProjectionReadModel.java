package com.example.cartdemo.readmodels;

import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.example.cartdemo.entities.Cart;
import java.util.List;

@ReadModel(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
public record CartWithProjectionReadModel(
    UUID id,
    List<Item> items
) {

  @Projects(entity = Cart.class, joinKey = "id")
  public static ProjectionResult<CartWithProjectionReadModel> projectCart(Cart entity, CartWithProjectionReadModel currentCartWithProjectionReadModel) {
    return ProjectionResult.of(/* NEW CartWithProjectionReadModel HERE */);
  }
}
