package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.CarOwner;

/**
 * The public view of a car owner ({@code src/read-models/car-owner-read-model.ts}).
 *
 * @param id the owner
 * @param name the owner's name
 */
@ReadModel(authorize = All.class)
public record CarOwnerReadModel(UUID id, String name) {

  /**
   * @param owner the owner
   * @param old the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = CarOwner.class, joinKey = "id")
  public static ProjectionResult<CarOwnerReadModel> projectCarOwner(CarOwner owner, CarOwnerReadModel old) {
    return ProjectionResult.of(new CarOwnerReadModel(owner.id(), owner.name()));
  }
}
