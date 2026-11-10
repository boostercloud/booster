package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.events.CartChecked;
import com.boostercloud.integration.events.CartItemChanged;
import com.boostercloud.integration.events.ShippingAddressUpdated;
import java.util.ArrayList;
import java.util.List;

/**
 * A shopping cart ({@code src/entities/cart.ts}). A (mutable) class rather than a record because
 * {@link MigratedCart} extends it, as in TypeScript; the reducers mutate and return the current instance like the
 * TypeScript ones.
 */
@Entity(authorizeReadEvents = All.class)
public class Cart {

  /** The cart id. */
  protected UUID id;
  /** The cart lines. */
  protected List<CartItem> cartItems;
  /** Where to ship it, when known. */
  @Nullable
  protected Address shippingAddress;
  /** How many times it was checked ({@code 0} by default). */
  protected double checks;

  /** An empty cart (used when binding stored snapshots). */
  public Cart() {
    this.cartItems = new ArrayList<>();
  }

  /**
   * {@code new Cart(id, cartItems)}: no address, {@code checks = 0}.
   *
   * @param id the cart
   * @param cartItems its lines
   */
  public Cart(UUID id, List<CartItem> cartItems) {
    this(id, cartItems, null, 0);
  }

  /**
   * @param id the cart
   * @param cartItems its lines
   * @param shippingAddress where to ship it, or {@code null}
   * @param checks how many times it was checked
   */
  public Cart(UUID id, List<CartItem> cartItems, Address shippingAddress, double checks) {
    this.id = id;
    this.cartItems = cartItems == null ? new ArrayList<>() : new ArrayList<>(cartItems);
    this.shippingAddress = shippingAddress;
    this.checks = checks;
  }

  /**
   * @return the cart id
   */
  public UUID id() {
    return id;
  }

  /**
   * @return the cart lines (mutable, like the TypeScript array)
   */
  public List<CartItem> cartItems() {
    return cartItems;
  }

  /**
   * @return the shipping address, or {@code null}
   */
  public Address shippingAddress() {
    return shippingAddress;
  }

  /**
   * @return how many times it was checked
   */
  public double checks() {
    return checks;
  }

  /**
   * Called by reducers and projections to check they receive real instances (TypeScript issue #797).
   *
   * @return the cart id
   */
  public UUID getId() {
    return id;
  }

  /**
   * Adds the quantity to the product's line, or appends a new line; lines of {@link Constants#BEFORE_HOOK_PRODUCT_ID}
   * are never merged.
   *
   * @param event the event
   * @param currentCart the current cart, or {@code null}
   * @return the cart
   * @throws IllegalStateException for {@link Constants#REDUCER_ERROR_CART_ID}
   */
  @Reduces(CartItemChanged.class)
  public static Cart changeItem(CartItemChanged event, Cart currentCart) {
    if (Constants.REDUCER_ERROR_CART_ID.equals(event.cartId().value())) {
      throw new IllegalStateException(Constants.REDUCER_ERROR_CART_MESSAGE);
    }
    if (currentCart == null) {
      currentCart = new Cart(event.cartId(), new ArrayList<>());
    }
    currentCart.getId();
    event.getProductId();
    List<CartItem> items = currentCart.cartItems;
    int index = -1;
    for (int i = 0; i < items.size(); i++) {
      if (items.get(i).productId().equals(event.productId())) {
        index = i;
        break;
      }
    }
    if (index >= 0 && !Constants.BEFORE_HOOK_PRODUCT_ID.equals(items.get(index).productId().value())) {
      CartItem current = items.get(index);
      items.set(index, new CartItem(current.productId(), current.quantity() + event.quantity()));
    } else {
      items.add(new CartItem(event.productId(), event.quantity()));
    }
    return currentCart;
  }

  /**
   * @param event the event
   * @param currentCart the current cart, or {@code null}
   * @return the cart with the new address
   */
  @Reduces(ShippingAddressUpdated.class)
  public static Cart updatedShippingAddress(ShippingAddressUpdated event, Cart currentCart) {
    if (currentCart == null) {
      currentCart = new Cart(event.cartId(), new ArrayList<>());
    }
    currentCart.shippingAddress = event.address();
    return currentCart;
  }

  /**
   * @param event the event
   * @param currentCart the current cart, or {@code null}
   * @return the cart with one more check
   */
  @Reduces(CartChecked.class)
  public static Cart checkCart(CartChecked event, Cart currentCart) {
    if (currentCart == null) {
      currentCart = new Cart(event.cartId(), new ArrayList<>());
    }
    currentCart.checks += 1;
    return currentCart;
  }
}
