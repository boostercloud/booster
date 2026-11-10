package com.boostercloud.integration.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.Money;
import com.boostercloud.integration.events.CartItemChanged;
import com.boostercloud.integration.events.ProductCreated;
import com.boostercloud.integration.events.ProductUpdateReason;
import com.boostercloud.integration.events.ProductUpdated;
import com.boostercloud.integration.notifications.CartAbandoned;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The pure logic of the command handlers and hooks ({@code src/commands/*.ts}): what each one registers, the input
 * rewrites of the before hooks and the custom authorizer. The handlers that read entities or read models are covered
 * by the end-to-end suites.
 */
class CommandHandlersTest {

  private static final UUID CART = UUID.of("cart-1");

  private static Register register() {
    return new Register(UUID.of("request-1"), new LinkedHashMap<>(), ignored -> {});
  }

  private static Map<String, Object> input(String cartId) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("cartId", cartId);
    input.put("productId", "product-1");
    input.put("quantity", 3);
    return input;
  }

  @Nested
  class ChangeCartItemHooks {

    @Test
    void theFirstHookSetsTheNonExposedTestField() {
      Map<String, Object> result = ChangeCartItem.beforeFn(input("cart-1"), null);

      assertThat(result).containsEntry("test", 1).containsEntry("quantity", 3).containsEntry("cartId", "cart-1");
    }

    @Test
    void bothHooksRewriteTheMagicMutationInOrder() {
      Map<String, Object> first = ChangeCartItem.beforeFn(input(Constants.BEFORE_HOOK_MUTATION_ID), null);
      Map<String, Object> second = ChangeCartItem.beforeFnV2(first, null);

      assertThat(first).containsEntry("quantity", Constants.BEFORE_HOOK_QUANTITY)
          .containsEntry("cartId", Constants.BEFORE_HOOK_MUTATION_ID);
      assertThat(second).containsEntry("cartId", Constants.BEFORE_HOOK_MUTATION_ID_MODIFIED)
          .containsEntry("quantity", Constants.BEFORE_HOOK_QUANTITY)
          .containsEntry("test", 1);
    }

    @Test
    void theSecondHookLeavesOtherCartsAlone() {
      assertThat(ChangeCartItem.beforeFnV2(input("cart-1"), null)).isEqualTo(input("cart-1"));
    }

    @Test
    void theFirstHookFailsForTheMagicErrorCarts() {
      assertThatThrownBy(() -> ChangeCartItem.beforeFn(input(Constants.THROW_EXCEPTION_ID), null))
          .hasMessage(Constants.BEFORE_HOOK_EXCEPTION);
      assertThatThrownBy(() -> ChangeCartItem.beforeFn(input(Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_ID), null))
          .hasMessage(Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_MESSAGE);
    }

    @Test
    void theHandlerRegistersTheChangeOrFailsForTheHandlerErrorCarts() {
      Register register = register();
      ChangeCartItem.handle(new ChangeCartItem(CART, UUID.of("p"), 2, 1.0), register);

      assertThat(register.eventList()).containsExactly(new CartItemChanged(CART, UUID.of("p"), 2));
      for (String cartId : List.of(Constants.COMMAND_HANDLER_ERROR_CART_ID,
          Constants.COMMAND_HANDLER_ERROR_IGNORED_CART_ID)) {
        assertThatThrownBy(() -> ChangeCartItem.handle(new ChangeCartItem(UUID.of(cartId), UUID.of("p"), 2, 1.0),
            register()))
            .hasMessage(Constants.COMMAND_HANDLER_ERROR_CART_MESSAGE);
      }
    }
  }

  @Test
  void changeMultipleCartItemsRegistersOneUnitPerProductIdFromZero() {
    Register register = register();
    ChangeMultipleCartItems.handle(new ChangeMultipleCartItems(CART, 3), register);

    assertThat(register.eventList()).containsExactly(new CartItemChanged(CART, UUID.of("0"), 1),
        new CartItemChanged(CART, UUID.of("1"), 1), new CartItemChanged(CART, UUID.of("2"), 1));
  }

  @Test
  void changeMultipleCartItemsWithIdsRegistersTheItemsOfEveryCartInOrder() {
    Register register = register();
    ChangeMultipleCartItemsWithIds.handle(new ChangeMultipleCartItemsWithIds(List.of(
        Map.of("cartId", "a", "itemsCount", 2), Map.of("cartId", "b", "itemsCount", 1.0))), register);

    assertThat(register.eventList()).containsExactly(new CartItemChanged(UUID.of("a"), UUID.of("0"), 1),
        new CartItemChanged(UUID.of("a"), UUID.of("1"), 1), new CartItemChanged(UUID.of("b"), UUID.of("0"), 1));
  }

  @Test
  void createProductBuildsTheMoneyAndAppliesTheEventDefaults() {
    Register register = register();
    CreateProduct.handle(new CreateProduct("SKU", "Chair", "A chair", 1999, "EUR", UUID.of("p1"), null, null),
        register);

    assertThat(register.eventList()).containsExactly(new ProductCreated(UUID.of("p1"), "SKU", "Chair", "A chair",
        new Money(1999, "EUR"), Map.of(), "Other"));
  }

  @Test
  void createProductGeneratesAnIdWhenNoneIsGiven() {
    Register register = register();
    CreateProduct.handle(new CreateProduct("SKU", "Chair", "A chair", 1, "EUR", null, Map.of("color", "red"),
        "Furniture"), register);

    ProductCreated event = (ProductCreated) register.eventList().getFirst();
    assertThat(event.productId()).isNotNull();
    assertThat(event.productId().value()).isNotEmpty();
    assertThat(event.productDetails()).isEqualTo(Map.of("color", "red"));
    assertThat(event.productType()).isEqualTo("Furniture");
  }

  @Test
  void updateProductJoinsTheDescriptionsWithALineFeedAndDefaultsTheReason() {
    Register register = register();
    Money price = new Money(10, "EUR");
    UpdateProduct.handle(new UpdateProduct(UUID.of("p1"), "SKU", "Chair", "Short", "Long", price, null, false, null,
        null, null), register);

    assertThat(register.eventList()).containsExactly(new ProductUpdated(UUID.of("p1"), "SKU", "Chair",
        "Short\nLong", price, List.of(), false, ProductUpdateReason.CatalogChange, Map.of(), "Other"));
  }

  @Test
  void abandonCartRegistersTheNotificationPartitionedByTheCartId() {
    Register register = register();
    AbandonCart.handle(new AbandonCart(CART), register);

    assertThat(register.eventList()).containsExactly(new CartAbandoned("cart-1"));
  }

  @Test
  void emptyCommandAnswersAString() {
    assertThat(EmptyCommand.handle(new EmptyCommand(), register())).isEqualTo("Empty command executed");
  }

  @Test
  void logSomethingIsOnlyForUsersWithTheBooleanClaim() {
    LogSomething.CanLogSomething authorizer = new LogSomething.CanLogSomething();
    UserEnvelope allowed = new UserEnvelope(null, "u", List.of(), Map.of("canLogSomething", true), null);
    UserEnvelope stringClaim = new UserEnvelope(null, "u", List.of(), Map.of("canLogSomething", "true"), null);

    assertThatCode(() -> authorizer.authorize(allowed, null)).doesNotThrowAnyException();
    for (UserEnvelope denied : java.util.Arrays.asList(stringClaim, null)) {
      assertThatThrownBy(() -> authorizer.authorize(denied, null))
          .isInstanceOf(NotAuthorizedError.class)
          .hasMessage("You are not allowed to log something");
    }
  }
}
