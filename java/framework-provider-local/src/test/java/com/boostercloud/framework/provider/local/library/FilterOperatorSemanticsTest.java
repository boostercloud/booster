package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.services.ReadModelRegistry;
import com.boostercloud.framework.provider.local.store.JsRegExp;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

/**
 * Every Booster filter operator end to end on the local provider: the filter goes through
 * {@link SearcherAdapter#queryRecordFor} and {@link ReadModelAdapter#searchReadModel} into a real store, and the
 * matched read models are compared with what TypeScript's local provider returns for the same data. The expectations
 * follow from the TypeScript translation (spec 10, section 3.10) and NeDB 4.0.2's matching rules, case by case:
 * an array field matches when <em>any</em> element matches (also for {@code ne}, so an empty array never matches),
 * a missing field never equals {@code null} but is "not equal" to everything, comparisons only compare values of the
 * same type, regular expressions are unanchored searches that skip non-strings, {@code includes} with an object
 * matches elements that contain those fields.
 */
class FilterOperatorSemanticsTest {

  @TempDir
  Path project;

  private ReadModelRegistry registry;
  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
    registry = new ReadModelRegistry(project.resolve(".booster/read_models.json"));
    store("Product", map("id", "p1", "name", "Apple iPhone", "price", 1000, "rating", 4.5, "tags", list("phone", "apple"),
        "stock", 0, "active", true, "category", null, "specs", map("color", "black", "weight", 200),
        "variants", list(map("sku", "A1", "qty", 2), map("sku", "A2", "qty", 0)),
        "createdAt", "2026-01-10T10:00:00.000Z"));
    store("Product", map("id", "p2", "name", "apple pie", "price", 5.5, "rating", 3, "tags", list("food"),
        "stock", 10, "active", false, "specs", map("color", "golden"), "variants", list(),
        "createdAt", "2026-02-01T00:00:00.000Z"));
    store("Product", map("id", "p3", "name", "Banana", "price", "cheap", "tags", list(), "stock", 5, "active", true,
        "category", "fruit", "specs", null, "createdAt", "2025-12-31T23:59:59.999Z"));
    store("Product", map("id", "p4", "name", "C++ Primer", "price", 50, "rating", 5, "tags", list("book", "programming"),
        "stock", 1, "active", true, "category", "books", "specs", map("color", "white", "pages", 900),
        "variants", list(map("sku", "B1", "qty", 1)), "createdAt", "2026-03-15T12:30:00.000Z", "isbn", 123));
    // another read model type with matching data is never returned
    store("Other", map("id", "o1", "name", "Apple", "price", 1000, "active", true, "category", null,
        "tags", list("phone", "food", "book")));
  }

  @AfterEach
  void tearDown() {
    registry.readModels().close();
  }

  private void store(String typeName, Map<String, Object> value) {
    value.put("boosterMetadata", map("version", 1, "schemaVersion", 1));
    registry.store(map("typeName", typeName, "value", value), 0L);
  }

  private List<Object> search(Map<String, Object> filter) {
    return search(filter, null, null, null).items().stream().map(item -> item.get("id")).toList();
  }

  private ReadModelListResult<Map<String, Object>> search(Map<String, Object> filter, Map<String, Object> sortBy,
      Integer limit, Map<String, String> afterCursor) {
    return ReadModelAdapter.searchReadModel(registry, config, "Product", filter, sortBy, limit, afterCursor,
        afterCursor != null || limit != null, null);
  }

  private static Arguments row(String description, Map<String, Object> filter, String... expectedIds) {
    return Arguments.of(description, filter, List.of((Object[]) expectedIds));
  }

  static Stream<Arguments> operators() {
    return Stream.of(
        // eq: deep equality; arrays match any element; a missing field is not null
        row("eq string", map("name", map("eq", "Banana")), "p3"),
        row("eq integer", map("price", map("eq", 1000)), "p1"),
        row("eq double equal to an integer", map("price", map("eq", 1000.0)), "p1"),
        row("eq null only matches an explicit null", map("category", map("eq", null)), "p1"),
        row("eq boolean", map("active", map("eq", true)), "p1", "p3", "p4"),
        row("eq on an array field matches an element", map("tags", map("eq", "food")), "p2"),
        row("eq nested property", map("specs", map("color", map("eq", "black"))), "p1"),
        row("eq object is deep equality", map("specs", map("eq", map("color", "golden"))), "p2"),
        row("eq object with fewer fields does not match", map("specs", map("eq", map("color", "black")))),
        row("eq with an array operand uses its first element (Q4)", map("tags", map("eq", list("food", "phone"))),
            "p2"),
        row("eq uuid operand", map("id", map("eq", UUID.of("p2"))), "p2"),
        // ne: true for missing fields; on arrays it is "some element differs", so empty arrays never match
        row("ne null matches missing fields too", map("category", map("ne", null)), "p2", "p3", "p4"),
        row("ne boolean", map("active", map("ne", true)), "p2"),
        row("ne on an array field", map("tags", map("ne", "phone")), "p1", "p2", "p4"),
        row("ne number with a missing field", map("rating", map("ne", 3)), "p1", "p3", "p4"),
        // comparisons: only between two numbers, two strings or two dates
        row("gt number skips strings", map("price", map("gt", 10)), "p1", "p4"),
        row("lt number", map("price", map("lt", 10)), "p2"),
        row("gte number", map("price", map("gte", 50)), "p1", "p4"),
        row("lte number", map("price", map("lte", 5.5)), "p2"),
        row("gt string is UTF-16 order", map("name", map("gt", "B")), "p2", "p3", "p4"),
        row("gte ISO dates", map("createdAt", map("gte", "2026-01-01T00:00:00.000Z")), "p1", "p2", "p4"),
        row("lt ISO date prefix", map("createdAt", map("lt", "2026-01-01")), "p3"),
        row("gte double", map("rating", map("gte", 4)), "p1", "p4"),
        row("gt through an array of objects", map("variants", map("qty", map("gt", 1))), "p1"),
        row("gt zero", map("stock", map("gt", 0)), "p2", "p3", "p4"),
        row("only the first operator of an object is applied (Q3)", map("price", map("gte", 10, "lte", 100)),
            "p1", "p4"),
        // in
        row("in strings", map("category", map("in", list("fruit", "books"))), "p3", "p4"),
        row("in null does not match missing fields", map("category", map("in", list((Object) null))), "p1"),
        row("in on an array field", map("tags", map("in", list("food", "book"))), "p2", "p4"),
        row("in with a single value", map("price", map("in", 1000)), "p1"),
        row("in nothing", map("tags", map("in", list()))),
        // isDefined: null is defined
        row("isDefined true includes null", map("category", map("isDefined", true)), "p1", "p3", "p4"),
        row("isDefined false", map("category", map("isDefined", false)), "p2"),
        row("isDefined false on another field", map("rating", map("isDefined", false)), "p3"),
        row("isDefined nested", map("specs", map("pages", map("isDefined", true))), "p4"),
        // regular expressions: unanchored, case-sensitive unless iRegex, non-strings never match
        row("contains", map("name", map("contains", "pple")), "p1", "p2"),
        row("contains is case-sensitive", map("name", map("contains", "Apple")), "p1"),
        row("contains a lowercase letter", map("name", map("contains", "a")), "p2", "p3"),
        row("contains on an array of strings", map("tags", map("contains", "pho")), "p1"),
        row("contains on a field holding a string in one document", map("price", map("contains", "che")), "p3"),
        row("contains on a number field", map("isbn", map("contains", "12"))),
        row("contains is a regular expression source", map("name", map("contains", "C\\+\\+")), "p4"),
        row("contains with a literal brace (JavaScript Annex B)", map("name", map("contains", "{")), new String[0]),
        row("beginsWith", map("name", map("beginsWith", "B")), "p3"),
        row("beginsWith is case-sensitive", map("name", map("beginsWith", "apple")), "p2"),
        row("regex", map("name", map("regex", "^[AB]")), "p1", "p3"),
        row("regex with an end anchor", map("name", map("regex", "e$")), "p1", "p2"),
        row("iRegex", map("name", map("iRegex", "^apple")), "p1", "p2"),
        // includes: a substring search for strings, an element match otherwise
        row("includes string on an array", map("tags", map("includes", "book")), "p4"),
        row("includes string is a substring search", map("tags", map("includes", "pro")), "p4"),
        row("includes string on a string field", map("name", map("includes", "Prim")), "p4"),
        row("includes object", map("variants", map("includes", map("sku", "A1", "qty", 2))), "p1"),
        row("includes object matches elements containing those fields", map("variants", map("includes",
            map("sku", "A1"))), "p1"),
        row("includes object with a different value", map("variants", map("includes", map("sku", "A1", "qty", 3)))),
        row("includes number", map("tags", map("includes", 5))),
        row("includes on a non-array field", map("specs", map("includes", map("color", "black")))),
        // combinators
        row("and", map("and", list(map("price", map("gte", 10)), map("price", map("lte", 100)))), "p4"),
        row("or", map("or", list(map("category", map("eq", "fruit")), map("rating", map("gte", 5)))), "p3", "p4"),
        row("not", map("not", map("active", map("eq", true))), "p2"),
        row("not isDefined", map("not", map("category", map("isDefined", true))), "p2"),
        row("nested and/or/not", map("and", list(
            map("or", list(map("name", map("beginsWith", "A")), map("name", map("beginsWith", "B")))),
            map("not", map("stock", map("eq", 0))))), "p3"),
        row("several properties are an implicit and", map("active", map("eq", true), "stock", map("gt", 0)),
            "p3", "p4"),
        row("no filter", map(), "p1", "p2", "p3", "p4"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("operators")
  void operatorsMatchTheTypeScriptLocalProvider(String description, Map<String, Object> filter, List<Object> ids) {
    assertThat(search(filter)).containsExactlyInAnyOrderElementsOf(ids);
    // the same filter, reloaded from disk, gives the same answer
    ReadModelRegistry reloaded = new ReadModelRegistry(project.resolve(".booster/read_models.json"));
    try {
      List<Object> found = ReadModelAdapter.searchReadModel(reloaded, config, "Product", filter, null, null, null,
          false, null).items().stream().map(item -> item.get("id")).toList();
      assertThat(found).containsExactlyInAnyOrderElementsOf(ids);
    } finally {
      reloaded.readModels().close();
    }
  }

  @Test
  void operatorsThatNeedAStringOrAValidPatternFailLikeTypeScript() {
    assertThatThrownBy(() -> search(map("stock", map("contains", 1))))
        .hasMessage("Attempted to perform a contains operation on a non-string");
    assertThatThrownBy(() -> search(map("stock", map("beginsWith", 1))))
        .hasMessage("Attempted to perform a begins-with operation on a non-string");
    // new RegExp('^C++') throws in JavaScript (Java alone would read a possessive quantifier)
    assertThatThrownBy(() -> search(map("name", map("beginsWith", "C++"))))
        .isInstanceOf(JsRegExp.SyntaxError.class)
        .hasMessage("Invalid regular expression: /^C++/: Nothing to repeat");
    assertThatThrownBy(() -> search(map("name", map("regex", "(unclosed"))))
        .hasMessage("Invalid regular expression: /(unclosed/: Unterminated group");
  }

  @Test
  void sortingFollowsNedbOrderAcrossTypesAndOnlyAscSortsAscending() {
    // numbers before strings; missing values first
    assertThat(ids(search(map(), map("price", "ASC"), null, null))).containsExactly("p2", "p4", "p1", "p3");
    assertThat(ids(search(map(), map("price", "DESC"), null, null))).containsExactly("p3", "p1", "p4", "p2");
    // Q5: only the exact string ASC is ascending
    assertThat(ids(search(map(), map("price", "asc"), null, null))).containsExactly("p3", "p1", "p4", "p2");
    assertThat(ids(search(map(), map("rating", "ASC"), null, null))).containsExactly("p3", "p2", "p1", "p4");
    assertThat(ids(search(map(), map("specs", map("color", "ASC")), null, null)))
        .containsExactly("p3", "p1", "p2", "p4");
  }

  @Test
  void paginationIsAnOffsetCursor() {
    ReadModelListResult<Map<String, Object>> first = search(map(), map("price", "ASC"), 2, null);
    assertThat(ids(first)).containsExactly("p2", "p4");
    assertThat(first.cursor()).isEqualTo(Map.of("id", "2"));
    ReadModelListResult<Map<String, Object>> second = search(map(), map("price", "ASC"), 2, first.cursor());
    assertThat(ids(second)).containsExactly("p1", "p3");
    assertThat(second.cursor()).isEqualTo(Map.of("id", "4"));
    // Q15: the cursor is offset + requested limit, even past the end
    ReadModelListResult<Map<String, Object>> third = search(map(), map("price", "ASC"), 2, second.cursor());
    assertThat(third.items()).isEmpty();
    assertThat(third.cursor()).isEqualTo(Map.of("id", "6"));
  }

  @Test
  void selectProjectsTheValues() {
    List<Map<String, Object>> items = ReadModelAdapter.searchReadModel(registry, config, "Product",
        map("id", map("in", list("p1", "p3"))), map("id", "ASC"), null, null, false,
        List.of("name", "specs.color", "variants[].sku")).items();
    Map<String, Object> p1 = new LinkedHashMap<>();
    p1.put("name", "Apple iPhone");
    p1.put("specs", map("color", "black"));
    p1.put("variants", list(map("sku", "A1"), map("sku", "A2")));
    // p3: a null intermediate value and a missing array are skipped
    assertThat(items).containsExactly(p1, map("name", "Banana"));
  }

  private static List<Object> ids(ReadModelListResult<Map<String, Object>> result) {
    return result.items().stream().map(item -> item.get("id")).toList();
  }
}
