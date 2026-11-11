package com.boostercloud.integration.entities;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.events.CarModelAdded;
import com.boostercloud.integration.events.CarModelNameUpdated;

/**
 * A car model ({@code src/entities/car-model.ts}).
 *
 * @param id the model
 * @param name its name
 * @param brand its brand
 */
@Entity(authorizeReadEvents = All.class)
public record CarModel(UUID id, String name, String brand) {

  /**
   * @return the model id
   */
  public UUID getId() {
    return id;
  }

  /**
   * @param event the event
   * @param currentCarModel the current model (ignored)
   * @return the model
   */
  @Reduces(CarModelAdded.class)
  public static CarModel reduceCarModelAdded(CarModelAdded event, CarModel currentCarModel) {
    return new CarModel(event.id(), event.name(), event.brand());
  }

  /**
   * @param event the event
   * @param currentCarModel the current model
   * @return the renamed model
   * @throws IllegalStateException {@code CarModel not found} when there is no model
   */
  @Reduces(CarModelNameUpdated.class)
  public static CarModel reduceCarModelNameUpdated(CarModelNameUpdated event, CarModel currentCarModel) {
    if (currentCarModel == null) {
      throw new IllegalStateException("CarModel not found");
    }
    return new CarModel(currentCarModel.id(), event.newModelName(), currentCarModel.brand());
  }
}
