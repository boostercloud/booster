package com.boostercloud.framework.types.concepts;

/**
 * The non-upsert outcomes of a projection. Port of the numeric enum {@code ReadModelAction}
 * ({@code Delete = 0}, {@code Nothing = 1}); see {@link ProjectionResult}.
 */
@SuppressWarnings("rawtypes")
public enum ReadModelAction implements ProjectionResult {
  /** {@code 0}: delete the current read model. */
  DELETE(0),
  /** {@code 1}: leave the read model unchanged. */
  NOTHING(1);

  private final int value;

  ReadModelAction(int value) {
    this.value = value;
  }

  /**
   * @return the TypeScript numeric value
   */
  public int value() {
    return value;
  }
}
