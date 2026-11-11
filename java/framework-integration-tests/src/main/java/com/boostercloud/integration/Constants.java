package com.boostercloud.integration;

import java.util.List;

/**
 * Magic ids, messages and numbers shared by the application and the tests. Port of {@code src/constants.ts}; the
 * values are identical, the names are the TypeScript ones in upper snake case.
 */
public final class Constants {

  /** Product id whose cart lines are never merged by {@code Cart.changeItem}. */
  public static final String BEFORE_HOOK_PRODUCT_ID = "before-hook-product-id";
  /** Cart id whose {@code CartTotalQuantity} is multiplied by {@link #BEFORE_HOOK_QUERY_MULTIPLY}. */
  public static final String BEFORE_HOOK_QUERY_ID = "before-hook-query-id";
  /** Multiplier set by {@code CartTotalQuantity}'s before hook. */
  public static final int BEFORE_HOOK_QUERY_MULTIPLY = 10;
  /** Id that makes the before hooks of {@code ChangeCartItem} and {@code CartReadModel} throw. */
  public static final String THROW_EXCEPTION_ID = "throw-exception-id";
  /** Message thrown by those before hooks. */
  public static final String BEFORE_HOOK_EXCEPTION = "Before hook throwing exception";
  /** Cart id whose input is changed by the before hooks of {@code ChangeCartItem}. */
  public static final String BEFORE_HOOK_MUTATION_ID = "mutation-but-with-input-changes";
  /** The id {@link #BEFORE_HOOK_MUTATION_ID} becomes. */
  public static final String BEFORE_HOOK_MUTATION_ID_MODIFIED = BEFORE_HOOK_MUTATION_ID + "-modified";
  /** The quantity {@code ChangeCartItem.beforeFn} sets for {@link #BEFORE_HOOK_MUTATION_ID}. */
  public static final int BEFORE_HOOK_QUANTITY = 21;
  /** Cart id whose {@code ChangeCartItem} handler throws (error reported). */
  public static final String COMMAND_HANDLER_ERROR_CART_ID = "commandHandlerErrorCartId";
  /** Cart id whose {@code ChangeCartItem} handler throws (error swallowed by the global handler). */
  public static final String COMMAND_HANDLER_ERROR_IGNORED_CART_ID = "commandHandlerErrorIgnoredCartId";
  /** Message thrown by the {@code ChangeCartItem} handler. */
  public static final String COMMAND_HANDLER_ERROR_CART_MESSAGE = "commandHandlerErrorCartMessage";
  /** Message thrown by the {@code CartTotalQuantity} handler. */
  public static final String QUERY_HANDLER_ERROR_CART_MESSAGE = "queryHandlerErrorCartMessage";
  /** Cart id whose {@code ChangeCartItem} before hook throws. */
  public static final String COMMAND_HANDLER_BEFORE_ERROR_CART_ID = "commandHandlerBeforeErrorCartId";
  /** Cart id whose {@code CartTotalQuantity} handler throws. */
  public static final String QUERY_HANDLER_ERROR_CART_ID = "queryHandlerErrorCartId";
  /** Message thrown by the {@code ChangeCartItem} before hook. */
  public static final String COMMAND_HANDLER_BEFORE_ERROR_CART_MESSAGE = "commandHandlerBeforeErrorCartMessage";
  /** Product id whose {@code StockMoved} handler throws. */
  public static final String DISPATCH_EVENT_ERROR_CART_ID = "dispatchEventErrorCartId";
  /** Message thrown by the {@code StockMoved} handler. */
  public static final String DISPATCH_ERROR_CART_MESSAGE = "dispatchErrorCartMessage";
  /** Cart id whose {@code CartReadModel} projection throws. */
  public static final String PROJECTION_ERROR_CART_ID = "projectionErrorCartId";
  /** Message thrown by the {@code CartReadModel} projection. */
  public static final String PROJECTION_ERROR_CART_MESSAGE = "projectionErrorCartMessage";
  /** Cart id whose {@code Cart.changeItem} reducer throws. */
  public static final String REDUCER_ERROR_CART_ID = "reducerErrorCartId";
  /** Entity id rewritten by the global {@code onEventError} hook. */
  public static final String EVENT_ERROR_CART_ID = "eventErrorCartId";
  /** Entity id whose event errors are swallowed by the global {@code onEventError} hook. */
  public static final String IGNORE_EVENT_ERROR_CART_ID = "ignoreEventErrorCartId";
  /** Message thrown by the {@code Cart.changeItem} reducer. */
  public static final String REDUCER_ERROR_CART_MESSAGE = "reducerErrorCartMessage";

  /** Quantity selecting the carts of data migration V1. */
  public static final int QUANTITY_TO_MIGRATE_DATA = 1234;
  /** Quantity written by data migration V1 (and selecting the carts of V2). */
  public static final int QUANTITY_AFTER_DATA_MIGRATION_V1 = 1234999;
  /** Quantity written by data migration V2. */
  public static final int QUANTITY_AFTER_DATA_MIGRATION_V2 = 7777999;
  /** The ids data migration V2 gives to the migrated carts, in order. */
  public static final List<String> NEW_CART_IDS = List.of(
      "8f257da7-d2f5-4295-a971-89e194be8f97",
      "afb75ac8-6f5e-40f5-bc17-eb1a5b3afdf9",
      "ee81f141-9f58-415a-9643-74f88b35ceb8");

  private Constants() {}
}
