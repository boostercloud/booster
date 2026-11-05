package io.boostercloud.booster.core;
public record ReadModel(String name, Class<?> type, String sequenceKey) {
  public ReadModel(String name, Class<?> type) { this(name, type, null); }
}
