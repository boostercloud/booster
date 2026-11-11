package com.boostercloud.integration.commands;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Trace;
import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.events.CartItemChanged;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Changes the quantity of a product in a cart ({@code src/commands/change-cart-item.ts}). Two before hooks run in
 * order ({@link BeforeFn} then {@link BeforeFnV2}), {@code test} is not exposed in GraphQL (the first hook sets it)
 * and the handler is traced as {@code CHANGE_CART_ITEM_HANDLER}. The magic cart ids of {@link Constants} make the
 * hooks and the handler change the input or fail. The class declares exactly the TypeScript methods
 * ({@code beforeFn}, {@code beforeFnV2}, {@code handle}): its metadata is part of an error message.
 *
 * @param cartId the cart
 * @param productId the product
 * @param quantity the quantity to add
 * @param test set to 1 by {@link BeforeFn} (non-exposed)
 */
@Command(authorize = All.class, before = {ChangeCartItem.BeforeFn.class, ChangeCartItem.BeforeFnV2.class})
public record ChangeCartItem(UUID cartId, UUID productId, double quantity, @NonExposed Double test) {

  /**
   * {@code beforeFn}: quantity {@value Constants#BEFORE_HOOK_QUANTITY} for {@link Constants#BEFORE_HOOK_MUTATION_ID},
   * failures for {@link Constants#THROW_EXCEPTION_ID} and {@link Constants#COMMAND_HANDLER_BEFORE_ERROR_CART_ID},
   * then {@code test = 1}.
   *
   * @param input the raw GraphQL input
   * @param currentUser the user, or {@code null}
   * @return the changed input
   */
  public static Map<String, Object> beforeFn(Map<String, Object> input, UserEnvelope currentUser) {
    Map<String, Object> result = new LinkedHashMap<>(input);
    String cartId = result.get("cartId") == null ? null : result.get("cartId").toString();
    if (Constants.BEFORE_HOOK_MUTATION_ID.equals(cartId)) {
      result.put("quantity", Constants.BEFORE_HOOK_QUANTITY);
    } else if (Constants.THROW_EXCEPTION_ID.equals(cartId)) {
      throw new IllegalStateException(Constants.BEFORE_HOOK_EXCEPTION);
    } else if (Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_ID.equals(cartId)) {
      throw new IllegalStateException(Constants.COMMAND_HANDLER_BEFORE_ERROR_CART_MESSAGE);
    }
    result.put("test", 1);
    // console.log(await Promise.resolve())
    System.out.println("undefined");
    return result;
  }

  /**
   * {@code beforeFnV2}: appends {@code -modified} to {@link Constants#BEFORE_HOOK_MUTATION_ID}.
   *
   * @param input the raw GraphQL input (after {@link #beforeFn})
   * @param currentUser the user, or {@code null}
   * @return the changed input
   */
  public static Map<String, Object> beforeFnV2(Map<String, Object> input, UserEnvelope currentUser) {
    Map<String, Object> result = new LinkedHashMap<>(input);
    String cartId = result.get("cartId") == null ? null : result.get("cartId").toString();
    if (Constants.BEFORE_HOOK_MUTATION_ID.equals(cartId)) {
      result.put("cartId", cartId + "-modified");
    }
    // console.log(await Promise.resolve())
    System.out.println("undefined");
    return result;
  }

  /**
   * Registers {@link CartItemChanged}; fails with {@link Constants#COMMAND_HANDLER_ERROR_CART_MESSAGE} for the two
   * command-handler-error cart ids.
   *
   * @param command the command
   * @param register the register
   */
  @Trace("CHANGE_CART_ITEM_HANDLER")
  public static void handle(ChangeCartItem command, Register register) {
    String cartId = command.cartId() == null ? null : command.cartId().value();
    if (Constants.COMMAND_HANDLER_ERROR_CART_ID.equals(cartId)
        || Constants.COMMAND_HANDLER_ERROR_IGNORED_CART_ID.equals(cartId)) {
      throw new IllegalStateException(Constants.COMMAND_HANDLER_ERROR_CART_MESSAGE);
    }
    register.events(new CartItemChanged(command.cartId(), command.productId(), command.quantity()));
  }

  /** {@code before: [ChangeCartItem.beforeFn, ...]}. */
  public static final class BeforeFn implements CommandBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      return beforeFn(input, currentUser);
    }
  }

  /** {@code before: [..., ChangeCartItem.beforeFnV2]}. */
  public static final class BeforeFnV2 implements CommandBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      return beforeFnV2(input, currentUser);
    }
  }
}
