package com.boostercloud.integration.azurecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The SQL subset of the fake Cosmos DB: parsing, Cosmos DB semantics and the query plans of the gateway. */
class FakeCosmosSqlTest {

  private static final List<Map<String, Object>> DOCUMENTS = List.of(
      Map.of("id", "1", "kind", "event", "createdAt", "2026-01-02", "n", 2, "pk", "a"),
      Map.of("id", "2", "kind", "snapshot", "createdAt", "2026-01-01", "n", 1, "pk", "a"),
      Map.of("id", "3", "kind", "event", "createdAt", "2026-01-03", "pk", "b", "deletedAt", "2026-01-04"),
      Map.of("id", "4", "kind", "event", "createdAt", "2026-01-00", "n", "2", "pk", "b"));

  private static List<Object> run(String query, Map<String, Object> parameters) {
    return FakeCosmosSql.execute(FakeCosmosSql.parse(query), DOCUMENTS, parameters);
  }

  private static List<Object> ids(List<Object> rows) {
    return rows.stream().map(row -> ((Map<?, ?>) row).get("id")).map(Object.class::cast).toList();
  }

  @Test
  void filtersWithTheConditionsTheProviderSends() {
    assertThat(ids(run("SELECT * FROM c WHERE c[\"kind\"] = @kind AND c[\"createdAt\"] > @from AND NOT"
        + " IS_DEFINED(c[\"deletedAt\"]) ORDER BY c[\"createdAt\"] ASC", Map.of("@kind", "event", "@from", "2026-01-00"))))
        .containsExactly("1");
    assertThat(ids(run("SELECT * from c WHERE c.pk = @pk order by c.createdAt DESC", Map.of("@pk", "b"))))
        .containsExactly("3", "4");
    assertThat(ids(run("SELECT * FROM c WHERE c[\"id\"] IN (@a, @b)", Map.of("@a", "2", "@b", "4"))))
        .containsExactly("2", "4");
    assertThat(ids(run("SELECT * FROM c WHERE (c.kind = \"snapshot\" OR c.n = 2)", Map.of()))).containsExactly("1", "2");
  }

  @Test
  void comparisonsBetweenDifferentTypesOrWithMissingPropertiesAreUndefined() {
    // "2" (a string) is not 2, and a document without n is neither equal nor different
    assertThat(ids(run("SELECT * FROM c WHERE c.n = 2", Map.of()))).containsExactly("1");
    assertThat(ids(run("SELECT * FROM c WHERE c.n != 2", Map.of()))).containsExactly("2");
    assertThat(ids(run("SELECT * FROM c WHERE NOT (c.n > 1)", Map.of()))).containsExactly("2");
  }

  @Test
  void ordersPagesCountsAndProjects() {
    assertThat(ids(run("SELECT * FROM c WHERE c[\"pk\"] = @pk ORDER BY c[\"createdAt\"] DESC OFFSET 0 LIMIT 1",
        Map.of("@pk", "a")))).containsExactly("1");
    assertThat(run("SELECT VALUE COUNT(1) FROM c WHERE c.kind=\"event\"", Map.of())).containsExactly(3L);
    assertThat(run("SELECT TOP 1 1 FROM c", Map.of())).containsExactly(Map.of("$1", 1L));
    assertThat(run("SELECT DISTINCT c.pk FROM c", Map.of())).containsExactly(Map.of("pk", "a"), Map.of("pk", "b"));
  }

  @Test
  void plansCrossPartitionQueriesLikeTheGateway() {
    Map<String, Object> plain = FakeCosmosSql.plan(FakeCosmosSql.parse("SELECT * FROM c WHERE c.a = @a"));
    assertThat(((Map<?, ?>) plain.get("queryInfo")).get("rewrittenQuery")).isEqualTo("");
    assertThat(((Map<?, ?>) plain.get("queryInfo")).get("orderBy")).isEqualTo(List.of());

    Map<?, ?> ordered = (Map<?, ?>) FakeCosmosSql.plan(FakeCosmosSql.parse(
        "SELECT * from c WHERE c.pk = @pk order by c.createdAt DESC")).get("queryInfo");
    assertThat(ordered.get("orderBy")).isEqualTo(List.of("Descending"));
    String rewritten = (String) ordered.get("rewrittenQuery");
    assertThat(rewritten).isEqualTo("SELECT c._rid, [{\"item\": c.createdAt}] AS orderByItems, c AS payload FROM c WHERE"
        + " ({documentdb-formattableorderbyquery-filter}) AND (c.pk = @pk) ORDER BY c.createdAt DESC");
    // What the client sends to each partition key range
    List<Object> rows = run(rewritten.replace(FakeCosmosSql.ORDER_BY_FILTER, "true"), Map.of("@pk", "b"));
    assertThat(rows).extracting(row -> (Object) ((Map<?, ?>) ((Map<?, ?>) row).get("payload")).get("id")).containsExactly("3", "4");
    assertThat(((Map<?, ?>) rows.getFirst()).get("orderByItems")).isEqualTo(List.of(Map.of("item", "2026-01-03")));

    Map<?, ?> count = (Map<?, ?>) FakeCosmosSql.plan(FakeCosmosSql.parse("SELECT VALUE COUNT(1) FROM c")).get("queryInfo");
    assertThat(count.get("aggregates")).isEqualTo(List.of("Count"));
    assertThat(count.get("hasSelectValue")).isEqualTo(true);
    assertThat(FakeCosmosSql.parse("SELECT VALUE COUNT(1) FROM c").isGatewayServable()).isFalse();
    assertThat(FakeCosmosSql.parse("SELECT * FROM c WHERE c.a = 1").isGatewayServable()).isTrue();
  }

  @Test
  void rejectsWhatItDoesNotSupport() {
    assertThatThrownBy(() -> FakeCosmosSql.parse("SELECT * FROM c GROUP BY c.a"))
        .isInstanceOf(FakeCosmosSql.UnsupportedQueryException.class).hasMessageContaining("GROUP");
    assertThatThrownBy(() -> FakeCosmosSql.parse("SELECT * FROM c WHERE STARTSWITH(c.a, 'x')"))
        .isInstanceOf(FakeCosmosSql.UnsupportedQueryException.class).hasMessageContaining("STARTSWITH");
    assertThatThrownBy(() -> FakeCosmosSql.parse("SELECT * FROM r"))
        .isInstanceOf(FakeCosmosSql.UnsupportedQueryException.class);
    assertThatThrownBy(() -> run("SELECT * FROM c WHERE c.a = @missing", Map.of()))
        .isInstanceOf(FakeCosmosSql.UnsupportedQueryException.class).hasMessageContaining("@missing");
  }
}
