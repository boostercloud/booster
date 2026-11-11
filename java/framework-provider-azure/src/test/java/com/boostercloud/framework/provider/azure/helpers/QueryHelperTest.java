package com.boostercloud.framework.provider.azure.helpers;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Port of {@code test/helpers/query-helper.test.ts} plus the pagination rules the TypeScript tests leave uncovered. */
class QueryHelperTest {

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private String readModelName;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    cosmos = new CosmosMocks();
    readModelName = "SomeReadModel" + System.nanoTime();
  }

  private static Map<String, String> stringMap(String... keyValues) {
    Map<String, String> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private static SqlParameter p(String name, Object value) {
    return new SqlParameter(name, value);
  }

  private static Map<String, Object> includesItemFilter() {
    return obj(
        "days", obj("includes", 2),
        "items", obj("includes", obj("sku", "test", "price", obj("cents", 1000, "currency", "EUR"))));
  }

  @Nested
  class TheSearchMethod {

    @Test
    void executesASqlQueryWithoutFiltersInTheReadModelTable() {
      QueryHelper.search(cosmos.client, config, readModelName, obj());

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(readModelName);
      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec("SELECT * FROM c ", List.of()));
    }

    @Test
    void executesASqlQueryWithAStringProjectionInTheReadModelTable() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, false, null, "DISTINCT field");

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(readModelName);
      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec("SELECT DISTINCT field FROM c ", List.of()));
    }

    @Test
    void executesASqlQueryWithAProjectionForProjectionInTheReadModelTable() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, false, null,
          List.of("id", "other", "first.second.third"));

      assertThat(cosmos.querySpec().query())
          .isEqualTo("SELECT c[\"id\"], c[\"other\"], c[\"first\"][\"second\"][\"third\"] AS \"first.second.third\" FROM c ");
      assertThat(cosmos.querySpec().parameters()).isEmpty();
    }

    @Test
    void executesASqlQueryWithAProjectionThatHasArrayFieldsAndNestedObjects() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, false, null, List.of(
          "id", "other", "arrayProp[].prop1", "arrayProp[].prop2", "a.b.c1", "a.b.c2", "arr[].x.y", "arr[].x.z",
          "foo.items[].bar"));

      assertThat(cosmos.querySpec().query()).isEqualTo(
          "SELECT c[\"id\"], c[\"other\"], ARRAY(SELECT item[\"prop1\"], item[\"prop2\"] FROM item IN c[\"arrayProp\"]) AS arrayProp, "
              + "c[\"a\"][\"b\"][\"c1\"] AS \"a.b.c1\", c[\"a\"][\"b\"][\"c2\"] AS \"a.b.c2\", ARRAY(SELECT item[\"x\"][\"y\"], item[\"x\"][\"z\"] FROM item IN c[\"arr\"]) AS arr, "
              + "ARRAY(SELECT item[\"bar\"] FROM item IN c[\"foo\"][\"items\"]) AS \"foo.items\" "
              + "FROM c ");
      assertThat(cosmos.querySpec().parameters()).isEmpty();
    }

    @Test
    void executesASqlQueryWithAProjectionThatHasADeeplyNestedArrayOfObjects() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, false, null, List.of(
          "id", "x.arr[].z", "foo.bar.items[].id", "foo.bar.baz.items[].id", "arr[].subArr[].id", "arr[].id"));

      assertThat(cosmos.querySpec().query()).isEqualTo(
          "SELECT c[\"id\"], "
              + "ARRAY(SELECT item[\"z\"] FROM item IN c[\"x\"][\"arr\"]) AS \"x.arr\", "
              + "c[\"foo\"][\"bar\"][\"items\"] AS \"foo.bar.items\", c[\"foo\"][\"bar\"][\"baz\"][\"items\"] AS \"foo.bar.baz.items\", "
              + "ARRAY(SELECT item[\"subArr\"], item[\"id\"] FROM item IN c[\"arr\"]) AS arr "
              + "FROM c ");
    }

    @Test
    void executesASqlQueryWithAStarProjectionInTheReadModelTable() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, false, null, null);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec("SELECT * FROM c ", List.of()));
    }

    @Test
    void executesASqlQueryWithFiltersInTheReadModelTable() {
      Map<String, Object> filters = obj(
          "id", obj("eq", "3", "in", List.of("test1", "test2", "test3")),
          "stock", obj("gt", 0, "lte", 10));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(readModelName);
      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE "
              + "c[\"id\"] = @id_0 AND c[\"id\"] IN (@id_1_0,@id_1_1,@id_1_2) AND "
              + "c[\"stock\"] > @stock_0 AND c[\"stock\"] <= @stock_1",
          List.of(p("@id_0", "3"), p("@id_1_0", "test1"), p("@id_1_1", "test2"), p("@id_1_2", "test3"),
              p("@stock_0", 0), p("@stock_1", 10))));
    }

    @Test
    void supportsNotFilterCombinator() {
      Map<String, Object> filters = obj("id", obj("contains", "3"), "not", obj("id", obj("eq", "333")));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE CONTAINS(c[\"id\"], @id_0) AND NOT (c[\"id\"] = @id_1)",
          List.of(p("@id_0", "3"), p("@id_1", "333"))));
    }

    @Test
    void supportsAndAndOrFilterCombinators() {
      Map<String, Object> filters = obj(
          "id", obj("ne", "test"),
          "or", List.of(obj("id", obj("beginsWith", "1")), obj("id", obj("beginsWith", "2"))),
          "and", List.of(obj("id", obj("contains", "3")), obj("id", obj("contains", "4"))));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE c[\"id\"] <> @id_0 "
              + "AND (STARTSWITH(c[\"id\"], @id_1) or STARTSWITH(c[\"id\"], @id_2)) "
              + "AND (CONTAINS(c[\"id\"], @id_3) and CONTAINS(c[\"id\"], @id_4))",
          List.of(p("@id_0", "test"), p("@id_1", "1"), p("@id_2", "2"), p("@id_3", "3"), p("@id_4", "4"))));
    }

    @Test
    void supportsNestedPropertiesFilters() {
      Map<String, Object> filters = obj("mainItem", obj(
          "sku", obj("eq", "test"),
          "price", obj("cents", obj("gte", 1000, "lt", 100000))));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE c[\"mainItem\"][\"sku\"] = @sku_0 "
              + "AND c[\"mainItem\"][\"price\"][\"cents\"] >= @cents_0 AND c[\"mainItem\"][\"price\"][\"cents\"] < @cents_1",
          List.of(p("@sku_0", "test"), p("@cents_0", 1000), p("@cents_1", 100000))));
    }

    @Test
    void supportsArrayIncludesFilter() {
      QueryHelper.search(cosmos.client, config, readModelName, includesItemFilter());

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE ARRAY_CONTAINS(c[\"days\"], @days_0, true) AND ARRAY_CONTAINS(c[\"items\"], @items_0, true)",
          List.of(p("@days_0", 2),
              p("@items_0", obj("sku", "test", "price", obj("cents", 1000, "currency", "EUR"))))));
    }

    @Test
    void supportsOrderFor1Field() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, null, obj("sku", "DESC"), null);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec("SELECT * FROM c  ORDER BY c.sku DESC", List.of()));
    }

    @Test
    void supportsOrderForAnyNumberOfFields() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, null,
          obj("sku", "DESC", "price", "ASC"), null);

      assertThat(cosmos.querySpec())
          .isEqualTo(new SqlQuerySpec("SELECT * FROM c  ORDER BY c.sku DESC, c.price ASC", List.of()));
    }

    @Test
    void supportsOrderForNestedFields() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, null,
          obj("sku", "DESC", "address", obj("street", "ASC")), null);

      assertThat(cosmos.querySpec())
          .isEqualTo(new SqlQuerySpec("SELECT * FROM c  ORDER BY c.sku DESC, c.address.street ASC", List.of()));
    }

    @Test
    void supportsLimitedResults() {
      QueryHelper.search(cosmos.client, config, readModelName, includesItemFilter(), 3, null, false,
          obj("sku", "DESC", "price", "ASC"), null);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE ARRAY_CONTAINS(c[\"days\"], @days_0, true) AND ARRAY_CONTAINS(c[\"items\"], @items_0, true) ORDER BY c.sku DESC, c.price ASC OFFSET 0 LIMIT 3 ",
          List.of(p("@days_0", 2),
              p("@items_0", obj("sku", "test", "price", obj("cents", 1000, "currency", "EUR"))))));
    }

    @Test
    void supportsPaginatedAndLimitedResults() {
      QueryHelper.search(cosmos.client, config, readModelName, includesItemFilter(), 3, obj("id", "3"), true,
          obj("sku", "DESC", "price", "ASC"), null);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE ARRAY_CONTAINS(c[\"days\"], @days_0, true) AND ARRAY_CONTAINS(c[\"items\"], @items_0, true) ORDER BY c.sku DESC, c.price ASC OFFSET 3 LIMIT 3 ",
          List.of(p("@days_0", 2),
              p("@items_0", obj("sku", "test", "price", obj("cents", 1000, "currency", "EUR"))))));
      verify(cosmos.iterator).fetchAll();
    }

    @Test
    void supportsIsDefinedFilters() {
      Map<String, Object> filters = obj("and", List.of(
          obj("days", obj("isDefined", true)),
          obj("mainItem", obj("isDefined", false)),
          obj("mainItem", obj("sku", obj("isDefined", true)))));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE (IS_DEFINED(c[\"days\"]) and NOT IS_DEFINED(c[\"mainItem\"]) and IS_DEFINED(c[\"mainItem\"][\"sku\"]))",
          List.of()));
    }

    @Test
    void supportsIsDefinedFiltersWithComplexFilters() {
      Map<String, Object> filters = obj("and", List.of(
          obj("id", obj("eq", "3")),
          obj("mainItem", obj("sku", obj("eq", "test"))),
          obj("or", List.of(
              obj("days", obj("isDefined", true)),
              obj("items", obj("includes", obj("sku", "test", "price", obj("cents", 1000, "currency", "EUR")))))),
          obj("mainItem", obj("sku", obj("eq", null))),
          obj("mainItem", obj("price", obj("cents", obj("ne", null))))));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE (c[\"id\"] = @id_0 and c[\"mainItem\"][\"sku\"] = @sku_0 and (IS_DEFINED(c[\"days\"]) or ARRAY_CONTAINS(c[\"items\"], @items_0, true)) and c[\"mainItem\"][\"sku\"] = @sku_1 and c[\"mainItem\"][\"price\"][\"cents\"] <> @cents_0)",
          List.of(p("@id_0", "3"), p("@sku_0", "test"),
              p("@items_0", obj("sku", "test", "price", obj("cents", 1000, "currency", "EUR"))),
              p("@sku_1", null), p("@cents_0", null))));
    }

    @Test
    void throwsAnErrorWithNonSupportedFilters() {
      Map<String, Object> filters = obj("id", obj("existsIn", "test"));

      assertThatThrownBy(() -> QueryHelper.search(cosmos.client, config, readModelName, filters))
          .isInstanceOf(InvalidParameterError.class)
          .hasMessage("Operator \"existsIn\" is not supported");
    }
  }

  @Nested
  class FiltersBeyondTheTypeScriptTests {

    @Test
    void supportsRegexAndCaseInsensitiveRegex() {
      QueryHelper.search(cosmos.client, config, readModelName,
          obj("name", obj("regex", "^a", "iRegex", "b$"), "age", obj("gte", 18, "lt", 65)));

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE RegexMatch(c[\"name\"], @name_0) AND RegexMatch(c[\"name\"], @name_1, 'i') AND "
              + "c[\"age\"] >= @age_0 AND c[\"age\"] < @age_1",
          List.of(p("@name_0", "^a"), p("@name_1", "b$"), p("@age_0", 18), p("@age_1", 65))));
    }

    @Test
    void dropsTheSubIndexWhenAnInPlaceholderCollides() {
      Map<String, Object> filters = obj("or", List.of(
          obj("id", obj("in", List.of("a", "b"))),
          obj("id", obj("in", List.of("c", "d")))));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE (c[\"id\"] IN (@id_0_0,@id_0_1) or c[\"id\"] IN (@id_1,@id_2))",
          List.of(p("@id_0_0", "a"), p("@id_0_1", "b"), p("@id_1", "c"), p("@id_2", "d"))));
    }

    @Test
    void convertsFilterValuesToRawJsonFirst() {
      QueryHelper.search(cosmos.client, config, readModelName, obj("id", obj("eq", UUID.of("uuid-1"))));

      assertThat(cosmos.querySpec().parameters()).containsExactly(p("@id_0", "uuid-1"));
    }

    @Test
    void anEmptyInListProducesAnEmptyInClauseAsInTypeScript() {
      QueryHelper.search(cosmos.client, config, readModelName, obj("id", obj("in", List.of())));

      assertThat(cosmos.querySpec().query()).isEqualTo("SELECT * FROM c WHERE c[\"id\"] IN ()");
    }

    @Test
    void aNullPropertyFilterFailsLikeObjectEntriesOfNull() {
      assertThatThrownBy(() -> QueryHelper.search(cosmos.client, config, readModelName, obj("id", null)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Cannot convert undefined or null to object");
    }

    @Test
    void aNonArrayInValueFailsLikeValueMap() {
      assertThatThrownBy(() -> QueryHelper.search(cosmos.client, config, readModelName, obj("id", obj("in", "x"))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("value.map is not a function");
    }

    @Test
    void aBareRootTogetherWithItsChildrenSelectsOnlyTheChildren() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, false, null, List.of("a", "a.b"));

      assertThat(cosmos.querySpec().query()).isEqualTo("SELECT c[\"a\"][\"b\"] AS \"a.b\" FROM c ");
    }

    @Test
    void limitZeroAddsNoLimitWhenNotPaginated() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), 0, null, false, null, null);

      assertThat(cosmos.querySpec().query()).isEqualTo("SELECT * FROM c ");
    }
  }

  @Nested
  class Pagination {

    private FeedOptions feedOptions() {
      ArgumentCaptor<FeedOptions> captor = ArgumentCaptor.forClass(FeedOptions.class);
      verify(cosmos.items).query(any(SqlQuerySpec.class), captor.capture());
      return captor.getValue();
    }

    @Test
    void firstPageUsesContinuationTokensWithTheLimitAsPageSize() {
      when(cosmos.iterator.fetchNext()).thenReturn(
          new FeedResponse(List.of(obj("id", "1", "_etag", "\"e1\"")), "token-1", true, Map.of()));

      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj(), 5, null, true, null, null);

      assertThat(feedOptions()).isEqualTo(new FeedOptions(null, 5, null));
      verify(cosmos.iterator).fetchNext();
      verify(cosmos.iterator, never()).fetchAll();
      assertThat(result).isEqualTo(new ReadModelListResult<>(
          List.of(obj("id", "1", "_etag", "\"e1\"", "boosterMetadata", obj("optimisticConcurrencyValue", "\"e1\""))),
          1, stringMap("continuationToken", "token-1", "id", "5")));
    }

    @Test
    void nextPagesPassTheContinuationTokenAndAdvanceTheIdByTheLimit() {
      when(cosmos.iterator.fetchNext()).thenReturn(new FeedResponse(List.of(obj("id", "6")), null, false, Map.of()));

      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj(), 5,
          obj("continuationToken", "token-1", "id", "5"), true, null, null);

      assertThat(feedOptions()).isEqualTo(new FeedOptions("token-1", 5, null));
      assertThat(((ReadModelListResult<?>) result).cursor()).isEqualTo(Map.of("id", "10"));
    }

    @Test
    void theDefaultPageSizeIs100AndAnEmptyLastPageHasNoCursor() {
      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj(), null, null, true, null, null);

      assertThat(feedOptions()).isEqualTo(new FeedOptions(null, 100, null));
      assertThat(result).isEqualTo(new ReadModelListResult<>(List.of(), 0, null));
    }

    @Test
    void aNumericCursorWithoutTokenUsesTheLegacyOffsetPathAndAlwaysReturnsACursor() {
      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj(), 2, obj("id", "4"), true, null, null);

      assertThat(cosmos.querySpec().query()).isEqualTo("SELECT * FROM c  OFFSET 4 LIMIT 2 ");
      verify(cosmos.items, never()).query(any(SqlQuerySpec.class), any(FeedOptions.class));
      assertThat(result).isEqualTo(new ReadModelListResult<>(List.of(), 0, Map.of("id", "6")));
    }

    @Test
    void distinctQueriesAlwaysUseOffsetLimit() {
      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj("kind", obj("eq", "event")), 3, null,
          true, null, "DISTINCT c.entityID");

      assertThat(cosmos.querySpec().query())
          .isEqualTo("SELECT DISTINCT c.entityID FROM c WHERE c[\"kind\"] = @kind_0 OFFSET 0 LIMIT 3 ");
      assertThat(((ReadModelListResult<?>) result).cursor()).isEqualTo(Map.of("id", "3"));
    }

    @Test
    void distinctQueriesParseANonNumericCursorPrefixLikeParseInt() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), 3, obj("id", "12abc"), true, null,
          "DISTINCT c.entityID");

      assertThat(cosmos.querySpec().query()).isEqualTo("SELECT DISTINCT c.entityID FROM c  OFFSET 12 LIMIT 3 ");
    }

    @Test
    void distinctQueriesWithATokenUseTheContinuationPath() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), null, obj("continuationToken", "tk"), true, null,
          "DISTINCT c.entityID");

      assertThat(feedOptions()).isEqualTo(new FeedOptions("tk", 100, null));
    }

    @Test
    void aNonNumericCursorIdIsNotALegacyCursorButStillCountsAsOffset() {
      when(cosmos.iterator.fetchNext()).thenReturn(new FeedResponse(List.of(obj("id", "x")), null, false, Map.of()));

      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj(), 5, obj("id", "12abc"), true, null,
          null);

      assertThat(feedOptions()).isEqualTo(new FeedOptions(null, 5, null));
      assertThat(((ReadModelListResult<?>) result).cursor()).isEqualTo(Map.of("id", "17"));
    }

    @Test
    void aNumberCursorIdIsNotALegacyCursor() {
      QueryHelper.search(cosmos.client, config, readModelName, obj(), 5, obj("id", 5), true, null, null);

      verify(cosmos.items).query(any(SqlQuerySpec.class), eq(new FeedOptions(null, 5, null)));
    }
  }

  @Nested
  class ResultProcessing {

    @Test
    void nestsDottedAliasesOfTopLevelKeysOnlyAndAddsTheEtagAsOptimisticConcurrencyValue() {
      cosmos.returnRows(List.of(obj(
          "id", "1", "a.b.c", 1, "a.b.d", 2, "items", List.of(obj("x.y", 1)), "_etag", "\"etag\"",
          "boosterMetadata", obj("version", 3))));

      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj());

      assertThat(result).isEqualTo(List.of(obj(
          "id", "1", "a", obj("b", obj("c", 1, "d", 2)), "items", List.of(obj("x.y", 1)), "_etag", "\"etag\"",
          "boosterMetadata", obj("version", 3, "optimisticConcurrencyValue", "\"etag\""))));
    }

    @Test
    void rowsWithoutEtagGetAnEmptyBoosterMetadata() {
      cosmos.returnRows(List.of(obj("entityID", "e1")));

      Object result = QueryHelper.search(cosmos.client, config, readModelName, obj());

      assertThat(result).isEqualTo(List.of(obj("entityID", "e1", "boosterMetadata", obj())));
    }

    @Test
    void nestPropertiesOverwritesFalsyIntermediates() {
      assertThat(QueryHelper.nestProperties(List.of(obj("a", 0, "a.b", 1), 3, List.of(obj("c.d", 2)))))
          .isEqualTo(List.of(obj("a", obj("b", 1)), 3, List.of(obj("c", obj("d", 2)))));
      assertThat(QueryHelper.nestProperties("not an object")).isEqualTo(obj());
    }
  }

  /**
   * Keys of JSON-typed filters (maps, unions, interfaces, external classes) are chosen by the GraphQL client: they are
   * written as escaped string literals and reduced to identifier characters in placeholder names, so they can never
   * change the shape of the SQL text (TypeScript pastes them verbatim, which allows SQL injection).
   */
  @Nested
  class PropertyNamesAreData {

    @Test
    void aClientChosenKeyCannotEscapeItsPropertyAccessor() {
      Map<String, Object> filters = obj(
          "userId", obj("eq", "alice"),
          "productDetails", obj("x\"]) OR (1=1) OR IS_DEFINED(c[\"x", obj("isDefined", true)));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE c[\"userId\"] = @userId_0 AND "
              + "IS_DEFINED(c[\"productDetails\"][\"x\\\"]) OR (1=1) OR IS_DEFINED(c[\\\"x\"])",
          List.of(p("@userId_0", "alice"))));
    }

    @Test
    void aClientChosenKeyCannotEscapeItsPlaceholder() {
      Map<String, Object> filters = obj(
          "userId", obj("eq", "alice"),
          "productDetails", obj("a OR 1=1 OR c.x", obj("eq", 1), "b\\", obj("in", List.of(2, 3))));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE c[\"userId\"] = @userId_0 AND "
              + "c[\"productDetails\"][\"a OR 1=1 OR c.x\"] = @a_OR_1_1_OR_c_x_0 AND "
              + "c[\"productDetails\"][\"b\\\\\"] IN (@b__0_0,@b__0_1)",
          List.of(p("@userId_0", "alice"), p("@a_OR_1_1_OR_c_x_0", 1), p("@b__0_0", 2), p("@b__0_1", 3))));
    }

    @Test
    void keysThatSanitiseToTheSamePlaceholderGetDistinctOnesInBothWalks() {
      Map<String, Object> filters = obj("meta", obj("a-b", obj("eq", 1), "a_b", obj("eq", 2), "a.b", obj("eq", 3)));

      QueryHelper.search(cosmos.client, config, readModelName, filters);

      assertThat(cosmos.querySpec()).isEqualTo(new SqlQuerySpec(
          "SELECT * FROM c WHERE c[\"meta\"][\"a-b\"] = @a_b_0 AND c[\"meta\"][\"a_b\"] = @a_b_1 AND "
              + "c[\"meta\"][\"a.b\"] = @a_b_2",
          List.of(p("@a_b_0", 1), p("@a_b_1", 2), p("@a_b_2", 3))));
    }

    @Test
    void controlCharactersAreEscapedAndNonAsciiCharactersKept() {
      assertThat(QueryHelper.buildFilterExpression(obj("meta", obj("tab\there", obj("isDefined", false),
          "café", obj("isDefined", true))), new java.util.ArrayList<>()))
          .isEqualTo("NOT IS_DEFINED(c[\"meta\"][\"tab\\there\"]) AND IS_DEFINED(c[\"meta\"][\"café\"])");
    }
  }

  @Nested
  class ReplaceOrDeleteItem {

    @Test
    void replacesWhenANewValueIsGiven() {
      Map<String, Object> newValue = obj("id", "1", "value", obj());

      QueryHelper.replaceOrDeleteItem(cosmos.client, "container", config, "1", "pk", newValue);

      verify(cosmos.database).container("container");
      verify(cosmos.container).item("1", "pk");
      verify(cosmos.item).replace(newValue);
      verify(cosmos.item, never()).delete();
    }

    @Test
    void deletesOtherwise() {
      QueryHelper.replaceOrDeleteItem(cosmos.client, "container", config, "1", "pk", null);

      verify(cosmos.item).delete();
      verify(cosmos.item, never()).replace(any());
    }
  }
}
