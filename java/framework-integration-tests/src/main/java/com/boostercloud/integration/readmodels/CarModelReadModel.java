package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.CarModel;

/**
 * The public view of a car model ({@code src/read-models/car-model-read-model.ts}).
 *
 * @param id the model
 * @param name its name
 * @param brand its brand
 */
@ReadModel(authorize = All.class)
public record CarModelReadModel(UUID id, String name, String brand) {

  /**
   * @param model the model
   * @param old the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = CarModel.class, joinKey = "id")
  public static ProjectionResult<CarModelReadModel> projectCarModel(CarModel model, CarModelReadModel old) {
    return ProjectionResult.of(new CarModelReadModel(model.id(), model.name(), model.brand()));
  }
}
