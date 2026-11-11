package com.boostercloud.framework.types.errors;

import java.util.Objects;

/**
 * Pairs an error with the context in which it happened, for core's global error dispatcher. Port of
 * {@code GlobalErrorContainer}. Not throwable. Dispatch is by <b>exact class</b>: every subclass is final, so a
 * {@code switch} with type patterns matches exactly one case, and a bare {@code GlobalErrorContainer} only reaches
 * the generic {@code onError} hook.
 */
public sealed class GlobalErrorContainer
    permits CommandHandlerGlobalError,
        QueryHandlerGlobalError,
        ScheduleCommandGlobalError,
        EventHandlerGlobalError,
        ReducerGlobalError,
        ProjectionGlobalError,
        SnapshotPersistHandlerGlobalError,
        EventGlobalError {

  private final Throwable originalError;

  /**
   * @param originalError the error that happened
   */
  public GlobalErrorContainer(Throwable originalError) {
    this.originalError = Objects.requireNonNull(originalError, "originalError");
  }

  /**
   * @return the error that happened
   */
  public Throwable originalError() {
    return originalError;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{originalError=" + originalError + "}";
  }
}
