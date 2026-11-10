package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Concurrency;

/**
 * Two projections of the same entity into the same read model ({@code src/read-models/other-concurrency-read-model.ts}):
 * with {@code id == otherId} each event writes the read model twice.
 *
 * @param id the read model
 * @param otherId the other join key
 */
@ReadModel(authorize = All.class)
public record OtherConcurrencyReadModel(UUID id, UUID otherId) {

  /**
   * @param concurrency the entity
   * @param concurrencyReadModel the current read model, or {@code null}
   * @return the read model keyed by the entity id
   */
  @Projects(entity = Concurrency.class, joinKey = "id")
  public static ProjectionResult<OtherConcurrencyReadModel> persisted(
      Concurrency concurrency, OtherConcurrencyReadModel concurrencyReadModel) {
    return ProjectionResult.of(new OtherConcurrencyReadModel(concurrency.id(), concurrency.otherId()));
  }

  /**
   * @param concurrency the entity
   * @param concurrencyReadModel the current read model, or {@code null}
   * @return the read model keyed by {@code otherId}
   */
  @Projects(entity = Concurrency.class, joinKey = "otherId")
  public static ProjectionResult<OtherConcurrencyReadModel> persistedByOtherId(
      Concurrency concurrency, OtherConcurrencyReadModel concurrencyReadModel) {
    return ProjectionResult.of(new OtherConcurrencyReadModel(concurrency.otherId(), concurrency.otherId()));
  }
}
