package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
public record Page(List<JsonNode> items, int count, String cursor) {
  public Page { items = List.copyOf(items); }
}
