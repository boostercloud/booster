package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarOfferAdded;
import java.util.List;

/**
 * A car offer ({@code src/entities/car-offers.ts}); {@code purchasesIds} is an array join key of
 * {@code CarPurchasesReadModel}.
 *
 * @param id the offer
 * @param name its name
 * @param purchasesIds the purchases it applies to
 */
@Entity(authorizeReadEvents = All.class)
public record CarOffers(UUID id, String name, List<String> purchasesIds) {

  /**
   * @return the offer id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentCarDriver the current offer (ignored)
   * @return the offer
   */
  @Reduces(CarOfferAdded.class)
  public static CarOffers reduceCarOfferAdded(CarOfferAdded event, CarOffers currentCarDriver) {
    return new CarOffers(event.id(), event.name(), event.purchasesIds());
  }
}
