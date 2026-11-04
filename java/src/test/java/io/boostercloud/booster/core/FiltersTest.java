package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FiltersTest {
  private JsonNode json(String text) { try { return Json.MAPPER.readTree(text); } catch (Exception error) { throw new IllegalArgumentException(error); } }
  private final JsonNode record = json("""
      {"id":"a","name":"Alice","age":30,"tags":["admin","reader"],"profile":{"city":"London"},"enabled":true,"nil":null}
      """);
  @Test void scalarCollectionNestedAndBooleanOperators() {
    assertTrue(Filters.matches(record, json("""{"name":{"beginsWith":"Al","contains":"lic","regex":"^A","iRegex":"alice"},"age":{"gte":30,"lte":30,"in":[20,30]},"tags":{"includes":"admin"},"profile.city":{"eq":"London"},"enabled":{"ne":false}}""")));
    assertTrue(Filters.matches(record, json("""{"and":[{"age":{"gt":20}},{"age":{"lt":40}}],"or":[{"name":{"eq":"Bob"}},{"profile":{"city":{"eq":"London"}}}],"not":{"age":{"eq":10}}}""")));
    assertFalse(Filters.matches(record, json("""{"age":{"gt":30}}""")));
    assertTrue(Filters.matches(record, json("""{"missing":{"isDefined":false},"nil":{"isDefined":true}}""")));
    assertTrue(Filters.matches(record, json("""{"age":{"eq":30.0}}""")));
  }
  @Test void invalidOperatorsAreRejectedEvenForEmptyData() {
    for (String invalid : List.of("[]", "{\"or\":true}", "{\"age\":5}", "{\"age\":{\"in\":3}}", "{\"name\":{\"regex\":\"[\"}}", "{\"age\":{\"eq\":1,\"typo\":2}}")) {
      var options = new QueryOptions(json(invalid), List.of(), 10, null);
      assertThrows(BoosterException.class, () -> ReadModelSearch.search(Map.of(), options), invalid);
    }
  }
  @Test void regexHasLinearTimeBehaviorAndRejectsUnsupportedBackreferences() {
    JsonNode longValue = Json.MAPPER.valueToTree(Map.of("value", "a".repeat(100_000) + "!"));
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertFalse(Filters.matches(longValue, json("""{"value":{"regex":"^(a+)+$"}}"""))));
    assertThrows(BoosterException.class, () -> Filters.matches(record, json("""{"name":{"regex":"(a)\\\\1"}}""")));
  }
  @Test void keysetPaginationKeepsStableTiesAndSurvivesDeletedCursorRow() {
    Map<String,JsonNode> models = new HashMap<>();
    models.put("b", json("{\"id\":\"b\",\"score\":10}"));
    models.put("a", json("{\"id\":\"a\",\"score\":10}"));
    models.put("c", json("{\"id\":\"c\",\"score\":20}"));
    var order = List.of(new Sort("score", Sort.Direction.ASC));
    var first = ReadModelSearch.search(models, new QueryOptions(null, order, 1, null));
    assertEquals("a", first.items().getFirst().path("id").asText()); assertEquals(3, first.count()); assertNotNull(first.cursor());
    models.remove("a"); models.put("0", json("{\"id\":\"0\",\"score\":1}"));
    var second = ReadModelSearch.search(models, new QueryOptions(null, order, 1, first.cursor()));
    assertEquals("b", second.items().getFirst().path("id").asText());
    var third = ReadModelSearch.search(models, new QueryOptions(null, order, 1, second.cursor()));
    assertEquals("c", third.items().getFirst().path("id").asText()); assertNull(third.cursor());
    assertThrows(BoosterException.class, () -> ReadModelSearch.search(models, new QueryOptions(null, List.of(new Sort("score", Sort.Direction.DESC)), 1, first.cursor())));
    assertThrows(BoosterException.class, () -> ReadModelSearch.search(models, new QueryOptions(null, order, 1, "not-a-cursor")));
  }
  @Test void queryOptionsAndPagesDoNotExposeMutableSourceRecords() {
    JsonNode filter = json("{\"age\":{\"eq\":30}}");
    var options = new QueryOptions(filter, List.of(), 10, null);
    ((com.fasterxml.jackson.databind.node.ObjectNode) filter.get("age")).put("eq", 99);
    var page = ReadModelSearch.search(Map.of("a", record), options);
    assertEquals(1, page.count());
    ((com.fasterxml.jackson.databind.node.ObjectNode) page.items().getFirst()).put("age", 1);
    assertEquals(30, record.path("age").asInt());
    assertThrows(BoosterException.class, () -> new QueryOptions(null, List.of(), 0, null));
  }
}
