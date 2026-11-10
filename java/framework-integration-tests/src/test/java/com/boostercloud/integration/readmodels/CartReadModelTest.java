package com.boostercloud.integration.readmodels;

import static com.boostercloud.integration.readmodels.ProjectionsTest.value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.entities.Cart;
import com.boostercloud.integration.entities.MigratedCart;
import com.boostercloud.integration.entities.Payment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The hooks, calculated fields and projections of {@link CartReadModel} ({@code src/read-models/cart-read-model.ts}). */
class CartReadModelTest {

  private static final Address ADDRESS = new Address("f", "l", "spain", "s", "11111", "a");
  private static final List<CartItem> ITEMS = List.of(new CartItem(UUID.of("p1"), 2), new CartItem(UUID.of("p2"), 1));

  private static ReadModelRequestEnvelope byId(String id) {
    return ReadModelRequestEnvelope.builder().className("CartReadModel").version(1)
        .key(new ReadModelRequestEnvelope.Key(UUID.of(id), null)).build();
  }

  private static ReadModelRequestEnvelope byFilter(Map<String, Object> filters) {
    return ReadModelRequestEnvelope.builder().className("CartReadModel").version(1).filters(filters).build();
  }

  @Test
  void theFirstHookFailsOnlyForTheMagicIdRequestedById() {
    assertThatThrownBy(() -> CartReadModel.beforeFn(byId(Constants.THROW_EXCEPTION_ID), null))
        .hasMessage(Constants.BEFORE_HOOK_EXCEPTION);
    ReadModelRequestEnvelope filtered = byFilter(Map.of("id", Map.of("eq", Constants.THROW_EXCEPTION_ID)));
    assertThat(CartReadModel.beforeFn(filtered, null)).isSameAs(filtered);
    ReadModelRequestEnvelope other = byId("cart-1");
    assertThat(CartReadModel.beforeFn(other, null)).isSameAs(other);
  }

  @Test
  void theSecondHookRewritesTheKeyOrReplacesTheFiltersOfTheMagicId() {
    ReadModelRequestEnvelope byKey = CartReadModel.beforeFnV2(byId("before-fn-test"));
    ReadModelRequestEnvelope byEq = CartReadModel.beforeFnV2(
        byFilter(Map.of("id", Map.of("eq", "before-fn-test"), "checks", Map.of("gt", 0))));

    assertThat(byKey.key().id()).isEqualTo(UUID.of("before-fn-test-modified"));
    assertThat(byKey.key().sequenceKey()).isNull();
    assertThat(byEq.filters()).isEqualTo(Map.of("id", Map.of("eq", "before-fn-test-modified")));
    ReadModelRequestEnvelope other = byFilter(Map.of("id", Map.of("eq", "cart-1")));
    assertThat(CartReadModel.beforeFnV2(other)).isSameAs(other);
  }

  @Test
  void theCalculatedFieldsDeriveFromTheStoredFields() {
    CartReadModel withAddress = new CartReadModel(UUID.of("c1"), ITEMS, 0, ADDRESS, null, null);
    CartReadModel empty = new CartReadModel(UUID.of("c2"), List.of(), 0, null, null, null);

    assertThat(withAddress.cartItemsSize()).isEqualTo(2);
    assertThat(withAddress.myAddress()).isEqualTo(ADDRESS);
    assertThat(empty.cartItemsSize()).isZero();
    assertThat(empty.myAddress()).isEqualTo(new Address("", "", "", "", "", ""));
    assertThat(empty.lastProduct()).isEmpty();
  }

  @Test
  void theNonExposedTestPropertyIsAlwaysOne() {
    assertThat(new CartReadModel(UUID.of("c1"), ITEMS, 0, null, null, null).testProperty()).isEqualTo(1);
    assertThat(new CartReadModel(UUID.of("c1"), ITEMS, 0, null, null, null, 7.0, 0).testProperty()).isEqualTo(1);
  }

  @Test
  void aCartOrMigratedCartIsProjectedKeepingThePayment() {
    Payment payment = new Payment(UUID.of("pay1"), UUID.of("c1"), "token");
    CartReadModel old = new CartReadModel(UUID.of("c1"), List.of(), 0, null, payment, null);

    CartReadModel projected = value(CartReadModel.updateWithCart(new Cart(UUID.of("c1"), ITEMS, ADDRESS, 3), old));
    CartReadModel migrated = value(CartReadModel.updateWithCart(new MigratedCart(UUID.of("m1"), ITEMS, null, 0), null));

    assertThat(projected).isEqualTo(new CartReadModel(UUID.of("c1"), ITEMS, 3, ADDRESS, payment, List.of("p1", "p2")));
    assertThat(migrated).isEqualTo(new CartReadModel(UUID.of("m1"), ITEMS, 0, null, null, List.of("p1", "p2")));
    assertThatThrownBy(() -> CartReadModel.updateWithCart(
        new Cart(UUID.of(Constants.PROJECTION_ERROR_CART_ID), ITEMS), null))
        .hasMessage(Constants.PROJECTION_ERROR_CART_MESSAGE);
  }

  @Test
  void aPaymentCreatesTheCartOrUpdatesItDroppingTheProductIds() {
    Payment payment = new Payment(UUID.of("pay1"), UUID.of("c1"), "token");
    CartReadModel old = new CartReadModel(UUID.of("c1"), ITEMS, 2, ADDRESS, null, List.of("p1", "p2"));

    assertThat(value(CartReadModel.updateCartPaymentStatus(payment, null)))
        .isEqualTo(new CartReadModel(UUID.of("c1"), List.of(), 0, null, payment, null));
    assertThat(value(CartReadModel.updateCartPaymentStatus(payment, old)))
        .isEqualTo(new CartReadModel(UUID.of("c1"), ITEMS, 2, ADDRESS, payment, null));
  }
}
