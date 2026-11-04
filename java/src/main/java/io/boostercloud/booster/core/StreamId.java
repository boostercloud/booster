package io.boostercloud.booster.core;

/** Stable event stream identity. */
public record StreamId(String entityType, String entityId) {
  public StreamId {
    if (entityType == null || entityType.isBlank() || entityId == null || entityId.isBlank())
      throw new IllegalArgumentException("Entity type and ID must not be blank");
  }
}
