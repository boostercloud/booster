package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.CarOffers;
import java.util.List;

/**
 * The public view of a car offer ({@code src/read-models/car-offer-read-model.ts}).
 *
 * @param id the offer
 * @param name its name
 * @param purchasesIds the purchases it applies to
 */
@ReadModel(authorize = All.class)
public record CarOfferReadModel(UUID id, String name, List<String> purchasesIds) {

  /**
   * @param offer the offer
   * @param old the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = CarOffers.class, joinKey = "id")
  public static ProjectionResult<CarOfferReadModel> projectCarOffers(CarOffers offer, CarOfferReadModel old) {
    return ProjectionResult.of(new CarOfferReadModel(offer.id(), offer.name(), offer.purchasesIds()));
  }
}
