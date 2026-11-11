package com.boostercloud.integration.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.events.CartChecked;
import com.boostercloud.integration.events.CartItemChanged;
import com.boostercloud.integration.events.ShippingAddressUpdated;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reducers of {@link Cart} ({@code src/entities/cart.ts}). */
class CartTest {

  private static final UUID CART = UUID.of("cart-1");
  private static final UUID PRODUCT = UUID.of("product-1");

  @Test
  void createsTheCartWithTheFirstItem() {
    Cart cart = Cart.changeItem(new CartItemChanged(CART, PRODUCT, 3), null);

    assertThat(cart.id()).isEqualTo(CART);
    assertThat(cart.cartItems()).containsExactly(new CartItem(PRODUCT, 3));
    assertThat(cart.checks()).isZero();
    assertThat(cart.shippingAddress()).isNull();
  }

  @Test
  void accumulatesTheQuantityOfAProductAlreadyInTheCart() {
    Cart cart = Cart.changeItem(new CartItemChanged(CART, PRODUCT, 3), null);
    cart = Cart.changeItem(new CartItemChanged(CART, UUID.of("other"), 1), cart);
    cart = Cart.changeItem(new CartItemChanged(CART, PRODUCT, 4), cart);

    assertThat(cart.cartItems()).containsExactly(new CartItem(PRODUCT, 7), new CartItem(UUID.of("other"), 1));
  }

  @Test
  void neverMergesTheLinesOfTheBeforeHookProduct() {
    UUID beforeHookProduct = UUID.of(Constants.BEFORE_HOOK_PRODUCT_ID);
    Cart cart = Cart.changeItem(new CartItemChanged(CART, beforeHookProduct, 1), null);
    cart = Cart.changeItem(new CartItemChanged(CART, beforeHookProduct, 2), cart);

    assertThat(cart.cartItems())
        .containsExactly(new CartItem(beforeHookProduct, 1), new CartItem(beforeHookProduct, 2));
  }

  @Test
  void failsForTheReducerErrorCart() {
    assertThatThrownBy(() -> Cart.changeItem(
        new CartItemChanged(UUID.of(Constants.REDUCER_ERROR_CART_ID), PRODUCT, 1), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(Constants.REDUCER_ERROR_CART_MESSAGE);
  }

  @Test
  void setsTheShippingAddressOfANewOrExistingCart() {
    Address address = new Address("f", "l", "spain", "s", "11111", "a");

    Cart created = Cart.updatedShippingAddress(new ShippingAddressUpdated(CART, address), null);
    Cart existing = Cart.updatedShippingAddress(new ShippingAddressUpdated(CART, address),
        new Cart(CART, List.of(new CartItem(PRODUCT, 1))));

    assertThat(created.shippingAddress()).isEqualTo(address);
    assertThat(created.cartItems()).isEmpty();
    assertThat(existing.shippingAddress()).isEqualTo(address);
    assertThat(existing.cartItems()).containsExactly(new CartItem(PRODUCT, 1));
  }

  @Test
  void countsTheChecks() {
    Cart cart = Cart.checkCart(new CartChecked(CART), null);
    cart = Cart.checkCart(new CartChecked(CART), cart);

    assertThat(cart.checks()).isEqualTo(2);
    assertThat(cart.cartItems()).isEmpty();
  }

  @Test
  void aMigratedCartIsACartWithItsOwnEntityName() {
    MigratedCart migrated = new MigratedCart(CART, List.of(new CartItem(PRODUCT, 1)), null, 2);

    assertThat(migrated).isInstanceOf(Cart.class);
    assertThat(migrated.getClass().getSimpleName()).isEqualTo("MigratedCart");
    assertThat(migrated.getId()).isEqualTo(CART);
    assertThat(migrated.checks()).isEqualTo(2);
  }
}
