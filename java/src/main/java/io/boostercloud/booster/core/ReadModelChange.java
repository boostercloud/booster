package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
public record ReadModelChange(String readModel, String id, JsonNode value, long position,
                              String sequenceKey, JsonNode sequenceValue) {
  public ReadModelChange(String readModel, String id, JsonNode value, long position) {
    this(readModel, id, value, position, null, null);
  }
  public ReadModelChange {
    value = value == null ? null : value.deepCopy();
    sequenceValue = sequenceValue == null ? null : sequenceValue.deepCopy();
  }
  @Override public JsonNode value() { return value == null ? null : value.deepCopy(); }
  @Override public JsonNode sequenceValue() { return sequenceValue == null ? null : sequenceValue.deepCopy(); }
}
