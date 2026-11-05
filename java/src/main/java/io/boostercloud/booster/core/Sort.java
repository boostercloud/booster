package io.boostercloud.booster.core;
public record Sort(String field, Direction direction) {
  public enum Direction { ASC, DESC }
  public Sort {
    if (field == null || field.isBlank()) throw new IllegalArgumentException("Sort field is required");
    if (direction == null) direction = Direction.ASC;
  }
}
