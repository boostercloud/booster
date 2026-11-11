package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.integration.events.StockMoved;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The stock of a product per warehouse ({@code src/entities/stock.ts}). Its events can only be read by users whose
 * {@code magicWord} claim is {@code opensesame}.
 *
 * @param id the product
 * @param warehouses the units per warehouse
 */
@Entity(authorizeReadEvents = Stock.MagicWord.class)
public record Stock(UUID id, Map<String, Double> warehouses) {

  /**
   * The first move creates the stock: the origin gets 0 ("it must come from a provider or a different dimension") and
   * the destination the quantity. Later moves subtract from the origin and add to the destination; as in JavaScript, a
   * warehouse not seen before becomes {@code NaN} (stored as {@code null}).
   *
   * @param event the event
   * @param currentStock the current stock, or {@code null}
   * @return the stock
   */
  @Reduces(StockMoved.class)
  public static Stock stockMoved(StockMoved event, Stock currentStock) {
    if (currentStock == null) {
      Map<String, Double> warehouses = new LinkedHashMap<>();
      warehouses.put(event.origin(), 0.0);
      warehouses.put(event.destination(), event.quantity());
      return new Stock(UUID.of(event.productID()), warehouses);
    }
    Map<String, Double> warehouses =
        currentStock.warehouses() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(currentStock.warehouses());
    warehouses.put(event.origin(), jsArithmetic(warehouses.get(event.origin()), -event.quantity()));
    warehouses.put(event.destination(), jsArithmetic(warehouses.get(event.destination()), event.quantity()));
    return new Stock(currentStock.id(), warehouses);
  }

  /** {@code undefined + n} is {@code NaN} in JavaScript; {@code null} plays {@code undefined}. */
  private static double jsArithmetic(Double current, double delta) {
    return current == null ? Double.NaN : current + delta;
  }

  /** {@code authorizeReadEvents}: rejects with {@code You don't know the magic word!} unless the claim matches. */
  public static final class MagicWord implements EventStreamAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, EventSearchRequest eventSearchRequest) {
      if (currentUser != null && "opensesame".equals(currentUser.claims().get("magicWord"))) {
        return;
      }
      throw new NotAuthorizedError("You don't know the magic word!");
    }
  }
}
