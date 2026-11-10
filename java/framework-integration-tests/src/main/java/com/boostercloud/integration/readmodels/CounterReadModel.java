package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.Counter;

/**
 * The public view of a {@link Counter} ({@code src/read-models/counter-read-model.ts}).
 *
 * @param id the counter
 * @param identifier its identifier
 * @param amount how many times it was incremented
 */
@ReadModel(authorize = All.class)
public record CounterReadModel(UUID id, String identifier, double amount) {

  /**
   * @param counter the counter
   * @param oldCounterReadModel the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = Counter.class, joinKey = "id")
  public static ProjectionResult<CounterReadModel> updateCounter(Counter counter, CounterReadModel oldCounterReadModel) {
    counter.getId();
    return ProjectionResult.of(new CounterReadModel(counter.id(), counter.identifier(), counter.amount()));
  }
}
