package com.boostercloud.framework.types.searcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.metadata.TypeGroup;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-types/test/searcher.test.ts}. */
class SearcherTest {

  record SomeModel(String someField) {}

  private SearcherFunction<SomeModel> searcherFunction;
  private FinderByKeyFunction<SomeModel> finderByKeyFunction;
  private Searcher<SomeModel> searcher;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    searcherFunction = mock(SearcherFunction.class);
    finderByKeyFunction = mock(FinderByKeyFunction.class);
    when(searcherFunction.search(any(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(List.of(new SomeModel("world")));
    when(finderByKeyFunction.find(any(), any(), any())).thenReturn(new SomeModel("world"));
    searcher = new Searcher<>(SomeModel.class, searcherFunction, finderByKeyFunction);
  }

  @Test
  void hasTheSearcherName() {
    assertThat(searcher.getClass().getSimpleName()).isEqualTo("Searcher");
  }

  @Nested
  class TheFilterMethod {
    @Test
    void addsAFilterForAProperty() {
      Map<String, Object> filter = Map.of("someField", Map.of("gt", "200"));
      Searcher<SomeModel> result = searcher.filter(filter);
      assertThat(result).isSameAs(searcher);
      assertThat(searcher.state().filters()).isEqualTo(filter);
    }

    @Test
    void replacesThePreviousFilter() {
      searcher.filter(Map.of("a", Map.of("eq", 1)));
      searcher.filter(Map.of("b", Map.of("eq", 2)));
      assertThat(searcher.state().filters()).containsOnlyKeys("b");
    }
  }

  @Nested
  class TheLimitMethod {
    @Test
    void setsTheLimit() {
      assertThat(searcher.limit(42)).isSameAs(searcher);
      assertThat(searcher.state().limit()).isEqualTo(42);
    }

    @Test
    void ignoresFalsyLimits() {
      searcher.limit(42).limit(0).limit(null);
      assertThat(searcher.state().limit()).isEqualTo(42);
    }
  }

  @Nested
  class TheAfterCursorMethod {
    @Test
    void setsTheCursor() {
      assertThat(searcher.afterCursor(42)).isSameAs(searcher);
      assertThat(searcher.state().afterCursor()).isEqualTo(42);
    }

    @Test
    void ignoresFalsyCursors() {
      searcher.afterCursor("30").afterCursor(0).afterCursor("").afterCursor(null).afterCursor(false);
      assertThat(searcher.state().afterCursor()).isEqualTo("30");
    }
  }

  @Nested
  class ThePaginatedVersionMethod {
    @Test
    void isFalseByDefaultAndCanBeSwitchedOn() {
      assertThat(searcher.state().paginatedVersion()).isFalse();
      assertThat(searcher.paginatedVersion(true)).isSameAs(searcher);
      assertThat(searcher.state().paginatedVersion()).isTrue();
    }

    @Test
    void cannotBeSwitchedOff() {
      searcher.paginatedVersion(true).paginatedVersion(false);
      assertThat(searcher.state().paginatedVersion()).isTrue();
    }
  }

  @Nested
  class TheSortByAndSelectMethods {
    @Test
    void ignoreNullValuesButKeepEmptySelections() {
      Map<String, Object> sort = SortFor.of("someField", "ASC");
      searcher.sortBy(sort).sortBy(null).select(List.of()).select(null);
      assertThat(searcher.state().sortBy()).isEqualTo(sort);
      assertThat(searcher.state().select()).isEmpty();
    }
  }

  @Nested
  @SuppressWarnings("deprecation")
  class TheFindByIdMethod {
    @Test
    void callsTheFinderByKeyFunctionWithASimpleKey() {
      searcher.findById(UUID.of("42"));
      verify(finderByKeyFunction).find(eq(SomeModel.class), eq(UUID.of("42")), isNull());
    }

    @Test
    void callsTheFinderByKeyFunctionWithACompoundKey() {
      SequenceKey sequenceKey = SequenceKey.of("timestamp", "1");
      searcher.findById(UUID.of("43"), sequenceKey);
      verify(finderByKeyFunction).find(SomeModel.class, UUID.of("43"), sequenceKey);
    }
  }

  @Nested
  class TheSearchOneMethod {
    @Test
    void callsTheSearcherFunctionDiscardingTheLimitAndPaginatedVersion() {
      Map<String, Object> filters = Map.of("someField", Map.of("gt", "200"));
      Optional<SomeModel> result = searcher.filter(filters).afterCursor("30").limit(50).paginatedVersion(true).searchOne();
      verify(searcherFunction).search(SomeModel.class, filters, Map.of(), 1, "30", false, null);
      assertThat(result).contains(new SomeModel("world"));
    }

    @Test
    void returnsEmptyWhenThereAreNoResults() {
      when(searcherFunction.search(any(), any(), any(), any(), any(), anyBoolean(), any())).thenReturn(List.of());
      assertThat(searcher.searchOne()).isEmpty();
    }
  }

  @Nested
  class TheSearchMethod {
    @Test
    void callsTheSearcherFunctionWithTheBuilderState() {
      Map<String, Object> filters = Map.of(
          "someField", Map.of("gt", "200"),
          "field", Map.of("otherField", Map.of("isDefined", true)));
      searcher.filter(filters).afterCursor("30").limit(50).paginatedVersion(true).search();
      verify(searcherFunction).search(SomeModel.class, filters, Map.of(), 50, "30", true, null);
    }

    @Test
    void returnsTheItemsOfPaginatedResults() {
      when(searcherFunction.search(any(), any(), any(), any(), any(), anyBoolean(), any()))
          .thenReturn(new ReadModelListResult<>(List.of(new SomeModel("a")), 1, Map.of("id", "1")));
      assertThat(searcher.paginatedVersion(true).search()).containsExactly(new SomeModel("a"));
      ReadModelListResult<SomeModel> page = searcher.searchPaginated();
      assertThat(page.count()).isEqualTo(1);
      assertThat(page.cursor()).containsEntry("id", "1");
    }

    @Test
    void wrapsPlainListsWhenAPageIsRequested() {
      ReadModelListResult<SomeModel> page = searcher.searchPaginated();
      assertThat(page.items()).containsExactly(new SomeModel("world"));
      assertThat(page.count()).isNull();
      verify(searcherFunction).search(SomeModel.class, Map.of(), Map.of(), null, null, true, null);
    }
  }

  @Nested
  class FilterAndSortGrammar {
    @Test
    void buildsFiltersWithTheTypeScriptShape() {
      Map<String, Object> filter = FilterFor.all(
          FilterFor.where("totalAmount", FilterFor.all(FilterFor.gte(50), FilterFor.lte(100))),
          FilterFor.where("items", FilterFor.includes(Map.of("productId", "p1"))),
          FilterFor.and(FilterFor.where("isActive", FilterFor.eq(true)), FilterFor.where("total", FilterFor.gte(50))),
          FilterFor.or(FilterFor.where("userId", FilterFor.eq("user-1")), FilterFor.where("userId", FilterFor.eq("user-2"))),
          FilterFor.not(FilterFor.where("isActive", FilterFor.eq(false))),
          FilterFor.isDefined(true));
      assertThat(filter).containsOnlyKeys("totalAmount", "items", "and", "or", "not", "isDefined");
      assertThat(filter.get("totalAmount")).isEqualTo(Map.of("gte", 50, "lte", 100));
      assertThat((List<?>) filter.get("and")).hasSize(2);
      assertThat(FilterFor.isOperationObject(filter.get("totalAmount"))).isTrue();
      assertThat(FilterFor.isOperationObject(Map.of("priority", Map.of("gte", 1)))).isFalse();
      assertThat(FilterFor.RESERVED_KEYS).containsExactlyInAnyOrder("and", "or", "not", "isDefined");
      assertThat(FilterFor.isCombinator("not")).isTrue();
      assertThat(FilterFor.isCombinator("isDefined")).isFalse();
    }

    @Test
    void knowsTheOperatorsOfEachTypeGroup() {
      assertThat(Operation.allowedFor(TypeGroup.ARRAY, false)).containsExactlyInAnyOrder(Operation.INCLUDES, Operation.IS_DEFINED);
      assertThat(Operation.allowedFor(TypeGroup.BOOLEAN, false))
          .containsExactlyInAnyOrder(Operation.EQ, Operation.NE, Operation.IS_DEFINED);
      assertThat(Operation.allowedFor(TypeGroup.STRING, false)).contains(Operation.REGEX, Operation.I_REGEX, Operation.BEGINS_WITH);
      assertThat(Operation.allowedFor(TypeGroup.NUMBER, false)).doesNotContain(Operation.CONTAINS);
      assertThat(Operation.allowedFor(TypeGroup.INTERFACE, true)).contains(Operation.GT, Operation.IN);
      assertThat(Operation.fromKey("iRegex")).isEqualTo(Operation.I_REGEX);
      assertThat(Operation.fromKey("and")).isNull();
    }

    @Test
    void acceptsTheFourSortSpellingsAndKeepsKeyOrder() {
      assertThat(SortDirection.parse("ASC")).isEqualTo(SortDirection.ASC);
      assertThat(SortDirection.parse("asc")).isEqualTo(SortDirection.ASC);
      assertThat(SortDirection.parse("DESC")).isEqualTo(SortDirection.DESC);
      assertThat(SortDirection.parse("desc")).isEqualTo(SortDirection.DESC);
      assertThatThrownBy(() -> SortDirection.parse("Asc")).isInstanceOf(IllegalArgumentException.class);
      Map<String, Object> sort = SortFor.of("id", SortDirection.ASC, "metadata", SortFor.of("priority", "DESC"), "name", "asc");
      assertThat(sort.keySet()).containsExactly("id", "metadata", "name");
      assertThat(sort.get("id")).isEqualTo("ASC");
      assertThat(SortDirection.isDirection(sort.get("metadata"))).isFalse();
    }

    @Test
    void understandsTheArrayConventionOfProjections() {
      List<String> projection = ProjectionFor.of("id", "cartItems[].productId");
      assertThat(projection).hasSize(2);
      assertThat(ProjectionFor.isArraySegment("cartItems[]")).isTrue();
      assertThat(ProjectionFor.propertyName("cartItems[]")).isEqualTo("cartItems");
      assertThat(ProjectionFor.propertyName("id")).isEqualTo("id");
    }
  }
}
