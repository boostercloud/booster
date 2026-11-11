package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Concurrency;

/**
 * One projection of {@link Concurrency}: its version grows by one per event
 * ({@code src/read-models/concurrency-read-model.ts}).
 *
 * @param id the entity
 */
@ReadModel(authorize = All.class)
public record ConcurrencyReadModel(UUID id) {

  /**
   * @param concurrency the entity
   * @param concurrencyReadModel the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = Concurrency.class, joinKey = "id")
  public static ProjectionResult<ConcurrencyReadModel> persisted(
      Concurrency concurrency, ConcurrencyReadModel concurrencyReadModel) {
    return ProjectionResult.of(new ConcurrencyReadModel(concurrency.id()));
  }
}
