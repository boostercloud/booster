package com.boostercloud.framework.types.errors;

import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;

/**
 * Context of an error thrown by a scheduled command (TypeScript spells it {@code Schedule}); hook
 * {@code onScheduledCommandHandlerError(error, envelope, metadata)}. Port of {@code ScheduleCommandGlobalError}; the original error is the last constructor argument.
 */
public final class ScheduleCommandGlobalError extends GlobalErrorContainer {
  private final ScheduledCommandEnvelope scheduleCommandEnvelope;
  private final ScheduledCommandMetadata scheduleCommandMetadata;

  /**
   * @param scheduleCommandEnvelope the scheduled command
   * @param scheduleCommandMetadata its metadata
   * @param originalError the error that happened
   */
  public ScheduleCommandGlobalError(ScheduledCommandEnvelope scheduleCommandEnvelope, ScheduledCommandMetadata scheduleCommandMetadata, Throwable originalError) {
    super(originalError);
    this.scheduleCommandEnvelope = scheduleCommandEnvelope;
    this.scheduleCommandMetadata = scheduleCommandMetadata;
  }

  /**
   * @return the scheduled command
   */
  public ScheduledCommandEnvelope scheduleCommandEnvelope() {
    return scheduleCommandEnvelope;
  }

  /**
   * @return its metadata
   */
  public ScheduledCommandMetadata scheduleCommandMetadata() {
    return scheduleCommandMetadata;
  }
}
