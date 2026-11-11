package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import java.util.List;

/**
 * The entity data migration V2 turns carts into ({@code MigratedCart extends Cart} in {@code src/entities/cart.ts}). It
 * declares no reducers: as in TypeScript, the {@code @Reduces} methods inherited from {@link Cart} are registered for
 * {@code Cart} only.
 */
@Entity(authorizeReadEvents = All.class)
public class MigratedCart extends Cart {

  /** An empty cart (used when binding stored snapshots). */
  public MigratedCart() {
    super();
  }

  /**
   * @param id the cart
   * @param cartItems its lines
   * @param shippingAddress where to ship it, or {@code null}
   * @param checks how many times it was checked
   */
  public MigratedCart(UUID id, List<CartItem> cartItems, Address shippingAddress, double checks) {
    super(id, cartItems, shippingAddress, checks);
  }
}
