package com.boostercloud.integration.readmodels;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.integration.entities.LogEventTest;

/**
 * The end of the chain {@code LogEvent} command → global handler → {@code LogEventTest}
 * ({@code src/read-models/log-event-received-test-read-model.ts}).
 *
 * @param id the entity
 * @param value the logged value
 */
@ReadModel(authorize = All.class)
public record LogEventReceivedTestReadModel(UUID id, String value) {

  /**
   * @param eventCounter the entity
   * @param old the current read model, or {@code null}
   * @return the read model
   */
  @Projects(entity = LogEventTest.class, joinKey = "id")
  public static ProjectionResult<LogEventReceivedTestReadModel> updateEventCounter(
      LogEventTest eventCounter, LogEventReceivedTestReadModel old) {
    eventCounter.getId();
    return ProjectionResult.of(new LogEventReceivedTestReadModel(eventCounter.id(), eventCounter.value()));
  }
}
