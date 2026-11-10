package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarPurchased;

/**
 * A car purchase ({@code src/entities/car-purchase.ts}).
 *
 * @param id the purchase
 * @param model the bought model
 * @param owner the buyer
 */
@Entity(authorizeReadEvents = All.class)
public record CarPurchase(UUID id, CarModel model, CarOwner owner) {

  /**
   * @return the purchase id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentCarPurchase the current purchase (ignored)
   * @return the purchase (note the model/owner order swap with the event)
   */
  @Reduces(CarPurchased.class)
  public static CarPurchase reduceCarPurchase(CarPurchased event, CarPurchase currentCarPurchase) {
    return new CarPurchase(event.id(), event.model(), event.owner());
  }
}
