package com.boostercloud.framework.provider.local.store;

/**
 * JavaScript's {@code undefined}: the value of a missing property, distinct from an explicit {@code null}.
 *
 * <p>NeDB distinguishes the two everywhere ({@code $exists}, equality, sorting), so the store keeps them apart too:
 * {@link Model#getDotValue(Object, String)} returns {@link #INSTANCE} for a missing field, and callers may put it in a
 * query to express "{@code undefined}" (for example {@code { 'value.version': undefined }}, which matches nothing).
 * Document maps never contain it: {@link Model#deepCopy(Object)} drops keys whose value is undefined, like
 * {@code JSON.stringify} does.
 */
public enum Undefined {
  /** The single {@code undefined} value. */
  INSTANCE;

  @Override
  public String toString() {
    return "undefined";
  }
}
