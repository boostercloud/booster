package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarOwnerAdded;
import com.boostercloud.integration.events.CarOwnerNameUpdated;

/**
 * A car owner ({@code src/entities/car-owner.ts}).
 *
 * @param id the owner
 * @param name the owner's name
 */
@Entity(authorizeReadEvents = All.class)
public record CarOwner(UUID id, String name) {

  /**
   * @return the owner id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentCarOwner the current owner (ignored)
   * @return the owner
   */
  @Reduces(CarOwnerAdded.class)
  public static CarOwner reduceCarOwnerAdded(CarOwnerAdded event, CarOwner currentCarOwner) {
    return new CarOwner(event.id(), event.name());
  }

  /**
   * @param event the event
   * @param currentCarOwner the current owner
   * @return the renamed owner
   * @throws IllegalStateException {@code CarOwner not found} when there is no owner
   */
  @Reduces(CarOwnerNameUpdated.class)
  public static CarOwner reduceCarOwnerNameUpdated(CarOwnerNameUpdated event, CarOwner currentCarOwner) {
    if (currentCarOwner == null) {
      throw new IllegalStateException("CarOwner not found");
    }
    return new CarOwner(currentCarOwner.id(), event.newOwnerName());
  }
}
