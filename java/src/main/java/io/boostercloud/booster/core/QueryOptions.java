package io.boostercloud.booster.core;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
public record QueryOptions(JsonNode filter, List<Sort> sortBy, int limit, String afterCursor) {
  public QueryOptions {
    filter = filter == null ? null : filter.deepCopy();
    sortBy = sortBy == null ? List.of() : List.copyOf(sortBy);
    if (limit < 1 || limit > 1000) throw new BoosterException(BoosterException.Code.VALIDATION, "Limit must be between 1 and 1000");
  }
  @Override public JsonNode filter() { return filter == null ? null : filter.deepCopy(); }
  public static QueryOptions defaults() { return new QueryOptions(null, List.of(), 100, null); }
}
