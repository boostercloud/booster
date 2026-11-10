package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.provider.local.services.ReadModelRegistry;
import com.boostercloud.framework.provider.local.store.JsRegExp;
import com.boostercloud.framework.provider.local.store.NedbError;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/read-model-adapter.test.ts}. */
class ReadModelAdapterTest {

  private BoosterConfig mockConfig;
  private Map<String, Object> mockReadModel;
  private Logger logger;
  private ReadModelRegistry mockReadModelRegistry;

  @BeforeEach
  void setUp() {
    mockConfig = new BoosterConfig("test");
    mockConfig.appName = "nuke-button";
    mockConfig.enableSubscriptions = true;
    logger = mock(Logger.class);
    mockConfig.logger = logger;
    mockReadModelRegistry = mock(ReadModelRegistry.class);
    mockReadModel = Mocks.readModelEnvelope();
  }

  private String typeName(Map<String, Object> envelope) {
    return (String) envelope.get("typeName");
  }

  private void searchMock(Map<String, Object> filters) {
    searchMock(filters, null, null, null);
  }

  private void searchMock(Map<String, Object> filters, Map<String, Object> sortBy, Integer limit, Object afterCursor) {
    ReadModelAdapter.searchReadModel(mockReadModelRegistry, mockConfig, typeName(mockReadModel), filters, sortBy, limit,
        afterCursor, null, null);
  }

  private void verifyQuery(Map<String, Object> query) {
    verify(mockReadModelRegistry).query(query, null, 0, null, null);
  }

  @Nested
  class RawReadModelEventsToEnvelopes {
    @Test
    void shouldReturnAnEmptyArrayOfEnvelopes() {
      assertThat(ReadModelAdapter.rawReadModelEventsToEnvelopes(mockConfig, List.of())).isEmpty();
    }

    @Test
    void shouldReturnAnArrayOfEnvelopes() {
      Map<String, Object> value1 = Mocks.readModelEnvelope();
      ReadModelEnvelope value2 = new ReadModelEnvelope("T", map("id", "x"));
      assertThat(ReadModelAdapter.rawReadModelEventsToEnvelopes(mockConfig, List.of(value1, value2)))
          .containsExactly(new ReadModelEnvelope(typeName(value1), value1.get("value")), value2);
    }
  }

  @Nested
  class FetchReadModel {
    private String mockReadModelTypeName;
    private UUID mockReadModelID;

    @BeforeEach
    void setUp() {
      mockReadModelTypeName = Mocks.word();
      mockReadModelID = UUID.of(Mocks.uuid());
    }

    @Test
    void shouldCallReadModelRegistryQueryAndReturnAValue() {
      when(mockReadModelRegistry.query(any())).thenReturn(List.of(mockReadModel));
      List<Map<String, Object>> result = ReadModelAdapter.fetchReadModel(mockReadModelRegistry, mockConfig,
          mockReadModelTypeName, mockReadModelID);
      verify(mockReadModelRegistry, times(1)).query(map("value.id", mockReadModelID.value(), "typeName",
          mockReadModelTypeName));
      assertThat(result.get(0)).isEqualTo(mockReadModel.get("value"));
      verify(logger, never()).debug("[Booster]|read-model-adapter#fetchReadModel: ",
          "Read model " + mockReadModelTypeName + " with ID " + mockReadModelID + " not found");
      verify(logger).debug("[Booster]|read-model-adapter#fetchReadModel: ",
          "Loaded read model " + mockReadModelTypeName + " with ID " + mockReadModelID + " with result:",
          mockReadModel.get("value"));
    }

    @Test
    void shouldCallReadModelRegistryQueryAndNoResults() {
      when(mockReadModelRegistry.query(any())).thenReturn(List.of());
      List<Map<String, Object>> result = ReadModelAdapter.fetchReadModel(mockReadModelRegistry, mockConfig,
          mockReadModelTypeName, mockReadModelID);
      verify(mockReadModelRegistry, times(1)).query(map("value.id", mockReadModelID.value(), "typeName",
          mockReadModelTypeName));
      assertThat(result).isEmpty();
      verify(logger).debug("[Booster]|read-model-adapter#fetchReadModel: ",
          "Read model " + mockReadModelTypeName + " with ID " + mockReadModelID + " not found");
    }
  }

  @Nested
  class StoreReadModel {
    private GraphQLService graphQLService;

    @BeforeEach
    void setUp() {
      graphQLService = mock(GraphQLService.class);
      ReadModelAdapter.storeReadModel(graphQLService, mockReadModelRegistry, mockConfig, typeName(mockReadModel),
          Mocks.value(mockReadModel), 1L);
    }

    @Test
    void shouldCallReadModelRegistryStore() {
      verify(mockReadModelRegistry).store(map("typeName", typeName(mockReadModel), "value", mockReadModel.get("value")),
          1L);
    }

    @Test
    void shouldLogTheRightDebugMessage() {
      verify(logger).debug("[Booster]|read-model-adapter#storeReadModel: ", "Read model stored");
    }

    @Test
    void shouldNotifyTheSubscribers() {
      verify(graphQLService).handleNotificationSubscription(
          List.of(new ReadModelEnvelope(typeName(mockReadModel), mockReadModel.get("value"))));
      verify(logger).debug("[Booster]|read-model-adapter#storeReadModel: ", "Read model change notified");
    }

    @Test
    void uniqueViolationsBecomeOptimisticConcurrencyErrors() {
      doThrow(new NedbError("conflict", "uniqueViolated", null)).when(mockReadModelRegistry).store(any(), any());
      assertThatThrownBy(() -> ReadModelAdapter.storeReadModel(graphQLService, mockReadModelRegistry, mockConfig, "T",
          map("id", "x"), 3L)).isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class).hasMessage("conflict");
      verify(logger).warn("[Booster]|read-model-adapter#storeReadModel: ",
          "Unique violated storing ReadModel {\"id\":\"x\"} and expectedCurrentVersion 3");
      doThrow(new NedbError("other")).when(mockReadModelRegistry).store(any(), any());
      assertThatThrownBy(() -> ReadModelAdapter.storeReadModel(graphQLService, mockReadModelRegistry, mockConfig, "T",
          map("id", "x"), 3L)).isExactlyInstanceOf(NedbError.class).hasMessage("other");
    }
  }

  @Nested
  class SearchReadModel {
    @Test
    void emptyQuery() {
      searchMock(map());
      verifyQuery(map("typeName", typeName(mockReadModel)));
    }

    @Test
    void eq() {
      searchMock(map("foo", map("eq", 1)));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", 1));
    }

    @Test
    void ne() {
      searchMock(map("foo", map("ne", 1)));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$ne", 1)));
    }

    @Test
    void lt() {
      searchMock(map("foo", map("lt", 1)));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$lt", 1)));
    }

    @Test
    void gt() {
      searchMock(map("foo", map("gt", 1)));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$gt", 1)));
    }

    @Test
    void lte() {
      searchMock(map("foo", map("lte", 1)));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$lte", 1)));
    }

    @Test
    void gte() {
      searchMock(map("foo", map("gte", 1)));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$gte", 1)));
    }

    @Test
    void in() {
      searchMock(map("foo", map("in", list(1, 2, 3))));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$in", list(1, 2, 3))));
    }

    @Test
    void contains() {
      searchMock(map("foo", map("contains", "bar")));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$regex", new JsRegExp("bar"))));
    }

    @Test
    void includes() {
      searchMock(map("foo", map("includes", "bar")));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$regex", new JsRegExp("bar"))));
    }

    @Test
    void includesObject() {
      searchMock(map("foo", map("includes", map("bar", "baz"))));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$elemMatch", map("bar", "baz"))));
    }

    @Test
    void beginsWith() {
      searchMock(map("foo", map("beginsWith", "bar")));
      verifyQuery(map("typeName", typeName(mockReadModel), "value.foo", map("$regex", new JsRegExp("^bar"))));
    }

    @Test
    void notBeginsWith() {
      searchMock(map("not", map("foo", map("beginsWith", "bar"))));
      verifyQuery(map("typeName", typeName(mockReadModel), "$not", map("value.foo", map("$regex",
          new JsRegExp("^bar")))));
    }

    @Test
    void onlyFieldsQueryUsesAnd() {
      searchMock(map("foo", map("subFooField", map("eq", "subFooField")), "bar", map("subBarField", map("eq",
          "subBarField")), "other", map("subOtherField", map("ne", true))));
      verifyQuery(map("value.foo.subFooField", "subFooField", "value.bar.subBarField", "subBarField",
          "value.other.subOtherField", map("$ne", true), "typeName", typeName(mockReadModel)));
    }

    @Test
    void gtLtAnd() {
      searchMock(map("and", list(map("foo", map("gt", 1)), map("foo", map("lt", 10)))));
      verifyQuery(map("typeName", typeName(mockReadModel), "$and", list(map("value.foo", map("$gt", 1)),
          map("value.foo", map("$lt", 10)))));
    }

    @Test
    void gteLteAnd() {
      searchMock(map("and", list(map("foo", map("gte", 1)), map("foo", map("lte", 10)))));
      verifyQuery(map("typeName", typeName(mockReadModel), "$and", list(map("value.foo", map("$gte", 1)),
          map("value.foo", map("$lte", 10)))));
    }

    @Test
    void or() {
      searchMock(map("or", list(map("foo", map("eq", 1)), map("bar", map("lt", 10)))));
      verifyQuery(map("typeName", typeName(mockReadModel), "$or", list(map("value.foo", 1), map("value.bar",
          map("$lt", 10)))));
    }

    @Test
    void sortFieldsLimitsAndSkip() {
      // TypeScript passes an array of { field, order } here; the Java contract types sortBy as a map
      Map<String, Object> sortBy = map("ID", "DESC", "anotherField", "ASC");
      searchMock(map(), sortBy, 3, map("id", "5"));
      verify(mockReadModelRegistry).query(map("typeName", typeName(mockReadModel)), sortBy, 5, 3, null);
    }

    @Test
    void returnsTheValuesAndThePaginatedShape() {
      when(mockReadModelRegistry.query(any(), any(), anyDouble(), any(), any()))
          .thenReturn(List.of(mockReadModel, Mocks.readModelEnvelope()));
      ReadModelListResult<Map<String, Object>> plain = ReadModelAdapter.searchReadModel(mockReadModelRegistry,
          mockConfig, "T", map(), null, null, null, false, null);
      assertThat(plain.items()).hasSize(2).first().isEqualTo(mockReadModel.get("value"));
      assertThat(plain.count()).isNull();
      assertThat(plain.cursor()).isNull();
      ReadModelListResult<Map<String, Object>> paginated = ReadModelAdapter.searchReadModel(mockReadModelRegistry,
          mockConfig, "T", map(), null, 1, map("id", "5"), true, List.of("id"));
      assertThat(paginated.count()).isEqualTo(2);
      assertThat(paginated.cursor()).isEqualTo(Map.of("id", "6"));
      verify(mockReadModelRegistry).query(map("typeName", "T"), null, 5, 1, List.of("id"));
      assertThat(ReadModelAdapter.searchReadModel(mockReadModelRegistry, mockConfig, "T", map(), null, null, null, true,
          null).cursor()).isEqualTo(Map.of("id", "1"));
      assertThat(ReadModelAdapter.searchReadModel(mockReadModelRegistry, mockConfig, "T", map(), null, 2,
          map("id", "oops"), true, null).cursor()).isEqualTo(Map.of("id", "NaN"));
    }
  }

  @Nested
  class DeleteReadModel {
    @Test
    void deleteOneReadModel() {
      String expectedId = Mocks.uuid();
      Map<String, Object> readModel = map("id", expectedId, "age", 3, "foo", Mocks.word(), "boosterMetadata",
          map("version", 1, "schemaVersion", 1));
      ReadModelAdapter.deleteReadModel(mockReadModelRegistry, mockConfig, "readModel", readModel);
      verify(mockReadModelRegistry).deleteById(expectedId, "readModel");
    }

    @Test
    void registryErrorsAreSwallowed() {
      when(mockReadModelRegistry.deleteById(any(), any())).thenThrow(new IllegalStateException("boom"));
      ReadModelAdapter.deleteReadModel(mockReadModelRegistry, mockConfig, "readModel", map("id", "x"));
      verify(logger).warn("[Booster]|read-model-adapter#deleteReadModel: ", "Read model to delete readModel ID = x not found");
    }
  }
}
