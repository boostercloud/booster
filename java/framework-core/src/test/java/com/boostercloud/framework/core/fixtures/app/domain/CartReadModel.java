package com.boostercloud.framework.core.fixtures.app.domain;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A read model of the fixture application.
 *
 * @param id the id
 */
@ReadModel(authorize = All.class)
public record CartReadModel(UUID id) {
  /**
   * @param cart the cart
   * @param current the current read model
   * @return the new read model
   */
  @Projects(entity = Cart.class, joinKey = "id")
  public static ProjectionResult<CartReadModel> project(Cart cart, CartReadModel current) {
    return ProjectionResult.of(new CartReadModel(cart.id()));
  }
}
