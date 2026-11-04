package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
public record ReadModelChange(String readModel, String id, JsonNode value, long position) {
  public ReadModelChange { value = value == null ? null : value.deepCopy(); }
  @Override public JsonNode value() { return value == null ? null : value.deepCopy(); }
}
