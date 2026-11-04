package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ReadModelSearch {
  private ReadModelSearch() {}
  static Page search(Map<String, JsonNode> models, QueryOptions options) {
    Filters.matches(com.fasterxml.jackson.databind.node.NullNode.instance, options.filter());
    var matching = models.entrySet().stream().filter(entry -> Filters.matches(entry.getValue(), options.filter())).toList();
    String shape = Booster.hash(Booster.canonical(options.filter()) + Json.MAPPER.valueToTree(options.sortBy()));
    record Row(String id, JsonNode data, List<JsonNode> keys) {}
    var rows = new ArrayList<Row>();
    for (var entry : matching) rows.add(new Row(entry.getKey(), entry.getValue(), options.sortBy().stream().map(sort -> Filters.path(entry.getValue(), sort.field())).toList()));
    Comparator<Row> comparator = (first, second) -> {
      for (int index = 0; index < options.sortBy().size(); index++) {
        int comparison = Filters.compare(first.keys().get(index), second.keys().get(index));
        if (comparison != 0) return options.sortBy().get(index).direction() == Sort.Direction.DESC ? -comparison : comparison;
      }
      return first.id().compareTo(second.id());
    };
    rows.sort(comparator);
    Row after = null;
    if (options.afterCursor() != null) {
      try {
        if (options.afterCursor().length() > 16384) throw new IllegalArgumentException();
        JsonNode cursor = Json.MAPPER.readTree(Base64.getUrlDecoder().decode(options.afterCursor()));
        if (!cursor.path("shape").asText().equals(shape) || !cursor.path("id").isTextual()
            || !cursor.path("keys").isArray() || cursor.path("keys").size() != options.sortBy().size()) throw new IllegalArgumentException();
        List<JsonNode> keys = new ArrayList<>(); cursor.get("keys").forEach(keys::add);
        after = new Row(cursor.get("id").textValue(), null, keys);
      } catch (Exception error) { throw new BoosterException(BoosterException.Code.VALIDATION, "Invalid cursor or cursor belongs to a different filter/sort", error); }
    }
    List<Row> remaining = new ArrayList<>();
    for (var row : rows) if (after == null || comparator.compare(row, after) > 0) remaining.add(row);
    var selected = remaining.subList(0, Math.min(options.limit(), remaining.size()));
    String next = null;
    if (remaining.size() > selected.size()) {
      var last = selected.getLast();
      var cursor = Json.MAPPER.createObjectNode(); cursor.put("shape", shape); cursor.put("id", last.id()); cursor.set("keys", Json.MAPPER.valueToTree(last.keys()));
      next = Base64.getUrlEncoder().withoutPadding().encodeToString(cursor.toString().getBytes(StandardCharsets.UTF_8));
    }
    return new Page(selected.stream().map(row -> row.data().deepCopy()).map(JsonNode.class::cast).toList(), rows.size(), next);
  }
}
