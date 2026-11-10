package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.AnotherCounter;

/**
 * The public view of an {@link AnotherCounter} ({@code src/read-models/another-counter-read-model.ts}).
 *
 * @param id the counter
 * @param identifier its identifier
 * @param amount how many times it was incremented
 */
@ReadModel(authorize = All.class)
public record AnotherCounterReadModel(UUID id, String identifier, double amount) {

  /**
   * @param anotherCounter the counter
   * @param oldAnotherCounterReadModel the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = AnotherCounter.class, joinKey = "id")
  public static ProjectionResult<AnotherCounterReadModel> updateAnotherCounter(
      AnotherCounter anotherCounter, AnotherCounterReadModel oldAnotherCounterReadModel) {
    anotherCounter.getId();
    return ProjectionResult.of(
        new AnotherCounterReadModel(anotherCounter.id(), anotherCounter.identifier(), anotherCounter.amount()));
  }
}
