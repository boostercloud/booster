package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.CalculatedField;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.integration.Constants;
import com.boostercloud.integration.common.Address;
import com.boostercloud.integration.common.CartItem;
import com.boostercloud.integration.common.JsonStringify;
import com.boostercloud.integration.common.Nullable;
import com.boostercloud.integration.entities.Cart;
import com.boostercloud.integration.entities.MigratedCart;
import com.boostercloud.integration.entities.Payment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The public view of a cart ({@code src/read-models/cart-read-model.ts}); schema version 2 (see
 * {@code migrations.readmodels.cartreadmodel}). It projects {@link Cart}, {@link MigratedCart} (same method) and
 * {@link Payment} (by {@code cartId}), has three calculated fields and two non-exposed properties, and two before
 * hooks.
 *
 * <p>TypeScript's {@code @NonExposed private testProperty: number}, set to {@code 1} by the constructor, is the last
 * component here, always {@code 1}, so that the stored document is the TypeScript one.
 *
 * @param id the cart
 * @param cartItems its lines
 * @param checks how many times it was checked
 * @param shippingAddress where to ship it
 * @param payment its payment
 * @param cartItemsIds the product ids of its lines
 * @param test never set (non-exposed)
 * @param testProperty always 1 (non-exposed)
 */
@ReadModel(authorize = All.class, before = {CartReadModel.BeforeFn.class, CartReadModel.BeforeFnV2.class})
public record CartReadModel(UUID id, List<CartItem> cartItems, double checks, @Nullable Address shippingAddress,
    @Nullable Payment payment, @Nullable List<String> cartItemsIds, @NonExposed @Nullable Double test,
    @NonExposed double testProperty) {

  /** {@code this.testProperty = 1}. */
  public CartReadModel {
    testProperty = 1;
  }

  /**
   * The TypeScript constructor ({@code test} left undefined).
   *
   * @param id the cart
   * @param cartItems its lines
   * @param checks how many times it was checked
   * @param shippingAddress where to ship it, or {@code null}
   * @param payment its payment, or {@code null}
   * @param cartItemsIds the product ids of its lines, or {@code null}
   */
  public CartReadModel(UUID id, List<CartItem> cartItems, double checks, Address shippingAddress, Payment payment,
      List<String> cartItemsIds) {
    this(id, cartItems, checks, shippingAddress, payment, cartItemsIds, null, 1);
  }

  /**
   * Called by the projection to check it receives a real instance.
   *
   * @return the checks
   */
  public double getChecks() {
    return checks;
  }

  /**
   * @return the number of lines ({@code 0} without lines)
   */
  @CalculatedField(dependsOn = {"cartItems"})
  @Nullable
  public Double cartItemsSize() {
    return cartItems == null ? 0.0 : (double) cartItems.size();
  }

  /**
   * @return the shipping address, or an empty address
   */
  @CalculatedField(dependsOn = {"shippingAddress"})
  public Address myAddress() {
    return shippingAddress != null ? shippingAddress : new Address("", "", "", "", "", "");
  }

  /**
   * Reads another read model from a calculated field.
   *
   * @return the product read model of the last line, if any
   */
  @CalculatedField(dependsOn = {"cartItems"})
  public Optional<ProductReadModel> lastProduct() {
    Double size = cartItemsSize();
    if (size == null || size == 0) {
      return Optional.empty();
    }
    String lastProductId = cartItems.getLast().productId() == null ? null : cartItems.getLast().productId().value();
    Map<String, Object> eq = new LinkedHashMap<>();
    eq.put("eq", lastProductId);
    return Booster.readModel(ProductReadModel.class).filter(Map.of("id", eq)).searchOne();
  }

  /**
   * Throws {@link Constants#BEFORE_HOOK_EXCEPTION} for a get-by-id of {@link Constants#THROW_EXCEPTION_ID}.
   *
   * @param request the request
   * @param currentUser the user, or {@code null}
   * @return the request
   */
  public static ReadModelRequestEnvelope beforeFn(ReadModelRequestEnvelope request, UserEnvelope currentUser) {
    String id = request != null && request.key() != null && request.key().id() != null
        ? request.key().id().value() : null;
    if (id != null && !id.isEmpty() && id.equals(Constants.THROW_EXCEPTION_ID)) {
      throw new IllegalStateException(Constants.BEFORE_HOOK_EXCEPTION);
    }
    return request;
  }

  /**
   * Reads {@code before-fn-test} as {@code before-fn-test-modified}: by id (dropping the sequence key) or by replacing
   * every filter with {@code { id: { eq: <new id> } }}.
   *
   * @param request the request
   * @return the request, possibly rewritten
   */
  public static ReadModelRequestEnvelope beforeFnV2(ReadModelRequestEnvelope request) {
    String id = idOf(request);
    System.out.println("Running `beforeFnV2` with ID = " + (id == null ? "undefined" : id) + ", and request = "
        + stringify(request));
    if (id == null || !id.equals("before-fn-test")) {
      return request;
    }
    String newId = id + "-modified";
    if (request.key() != null && request.key().id() != null && !request.key().id().value().isEmpty()) {
      return request.toBuilder().key(new ReadModelRequestEnvelope.Key(UUID.of(newId), null)).build();
    }
    Map<String, Object> eq = new LinkedHashMap<>();
    eq.put("eq", newId);
    Map<String, Object> filters = new LinkedHashMap<>();
    filters.put("id", eq);
    return request.toBuilder().filters(filters).build();
  }

  /** {@code request.key?.id || request.filters?.id?.eq}. */
  private static String idOf(ReadModelRequestEnvelope request) {
    if (request.key() != null && request.key().id() != null && !request.key().id().value().isEmpty()) {
      return request.key().id().value();
    }
    if (request.filters() != null && request.filters().get("id") instanceof Map<?, ?> idFilter) {
      Object eq = idFilter.get("eq");
      return eq == null || eq.toString().isEmpty() ? null : eq.toString();
    }
    return null;
  }

  private static String stringify(Object value) {
    return JsonStringify.stringify(value);
  }

  /**
   * Projects a cart (or a migrated cart) keeping the payment of the current read model.
   *
   * @param cart the cart
   * @param oldCartReadModel the current read model, or {@code null}
   * @return the read model
   * @throws IllegalStateException for {@link Constants#PROJECTION_ERROR_CART_ID}
   */
  @Projects(entity = Cart.class, joinKey = "id")
  @Projects(entity = MigratedCart.class, joinKey = "id")
  public static ProjectionResult<CartReadModel> updateWithCart(Cart cart, CartReadModel oldCartReadModel) {
    if (Constants.PROJECTION_ERROR_CART_ID.equals(cart.id() == null ? null : cart.id().value())) {
      throw new IllegalStateException(Constants.PROJECTION_ERROR_CART_MESSAGE);
    }
    List<String> cartProductIds = cart.cartItems().stream()
        .map(item -> item.productId() == null ? null : item.productId().value())
        .toList();
    cart.getId();
    if (oldCartReadModel != null) {
      oldCartReadModel.getChecks();
    }
    return ProjectionResult.of(new CartReadModel(cart.id(), cart.cartItems(), cart.checks(), cart.shippingAddress(),
        oldCartReadModel == null ? null : oldCartReadModel.payment(), cartProductIds));
  }

  /**
   * Projects a payment into the cart it pays (dropping {@code cartItemsIds}, as in TypeScript).
   *
   * @param payment the payment
   * @param oldCartReadModel the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = Payment.class, joinKey = "cartId")
  public static ProjectionResult<CartReadModel> updateCartPaymentStatus(Payment payment, CartReadModel oldCartReadModel) {
    if (oldCartReadModel == null) {
      return ProjectionResult.of(new CartReadModel(payment.cartId(), List.of(), 0, null, payment, null));
    }
    return ProjectionResult.of(new CartReadModel(oldCartReadModel.id(), oldCartReadModel.cartItems(),
        oldCartReadModel.checks(), oldCartReadModel.shippingAddress(), payment, null));
  }

  /** {@code before: [CartReadModel.beforeFn, ...]}. */
  public static final class BeforeFn implements ReadModelBeforeFunction {
    @Override
    public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope request, UserEnvelope currentUser) {
      return beforeFn(request, currentUser);
    }
  }

  /** {@code before: [..., CartReadModel.beforeFnV2]}. */
  public static final class BeforeFnV2 implements ReadModelBeforeFunction {
    @Override
    public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope request, UserEnvelope currentUser) {
      return beforeFnV2(request);
    }
  }
}
