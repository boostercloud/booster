package io.boostercloud.booster.core;

import java.util.Objects;

/** Explicit projection outcomes, including baseline Delete and Nothing actions. */
public record ProjectionResult<R>(Action action, R value) {
  public enum Action { STORE, DELETE, NOTHING }
  public ProjectionResult { Objects.requireNonNull(action); if (action == Action.STORE) Objects.requireNonNull(value); }
  public static <R> ProjectionResult<R> store(R value) { return new ProjectionResult<>(Action.STORE, value); }
  public static <R> ProjectionResult<R> delete() { return new ProjectionResult<>(Action.DELETE, null); }
  public static <R> ProjectionResult<R> nothing() { return new ProjectionResult<>(Action.NOTHING, null); }
}
