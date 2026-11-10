package com.boostercloud.framework.provider.local.services;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.provider.local.store.JsRegExp;
import com.boostercloud.framework.provider.local.store.NedbError;
import com.boostercloud.framework.provider.local.store.UpdateOptions;
import com.boostercloud.framework.provider.local.store.UpdateResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/read-model-registry.test.ts} (on a temporary {@code .booster/read_models.json}). */
class ReadModelRegistryTest {

  @TempDir
  Path project;

  private int initialReadModelsCount;
  private Map<String, Object> mockReadModel;
  private ReadModelRegistry readModelRegistry;

  @BeforeEach
  void setUp() {
    initialReadModelsCount = ThreadLocalRandom.current().nextInt(2, 11);
    readModelRegistry = new ReadModelRegistry(project.resolve(".booster/read_models.json"));
    readModelRegistry.readModels().remove(map(), true);
  }

  @AfterEach
  void tearDown() {
    readModelRegistry.readModels().close();
  }

  private static Map<String, Object> value(Map<String, Object> envelope) {
    return Mocks.value(envelope);
  }

  /** {@code expect(result).to.deep.include(envelope)}. */
  private static void assertIncludes(Map<String, Object> result, Map<String, Object> envelope) {
    assertThat(result).containsAllEntriesOf(envelope);
  }

  @Nested
  class Query {
    @BeforeEach
    void seed() throws Exception {
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < initialReadModelsCount; i++) {
          Map<String, Object> envelope = Mocks.readModelEnvelope();
          executor.submit(() -> {
            readModelRegistry.store(envelope, 0L);
            return null;
          });
        }
      }
      mockReadModel = Mocks.readModelEnvelope();
      readModelRegistry.store(mockReadModel, 1L);
    }

    @Test
    void shouldReturnTheExpectedReadModel() {
      List<Map<String, Object>> result =
          readModelRegistry.query(map("value", value(mockReadModel), "typeName", mockReadModel.get("typeName")));
      assertThat(result).hasSize(1);
      assertIncludes(result.get(0), mockReadModel);
    }

    @Test
    void shouldReturnTheExpectedReadModelById() {
      List<Map<String, Object>> result =
          readModelRegistry.query(map("value.id", value(mockReadModel).get("id"), "typeName", mockReadModel.get("typeName")));
      assertThat(result).hasSize(1);
      assertIncludes(result.get(0), mockReadModel);
    }

    @Test
    void shouldReturnTheExpectedReadModelWhenAFieldDoesNotExist() {
      List<Map<String, Object>> result = readModelRegistry.query(map("value.id", value(mockReadModel).get("id"),
          "value.other", map("$exists", false), "typeName", mockReadModel.get("typeName")));
      assertThat(result).hasSize(1);
      assertIncludes(result.get(0), mockReadModel);
    }

    @Test
    void shouldReturnNoResultsWhenTheIdDoesNotMatch() {
      assertThat(readModelRegistry.query(map("value.id", Mocks.uuid(), "typeName", mockReadModel.get("typeName"))))
          .isEmpty();
    }

    @Test
    void shouldReturnNoResultsWhenTheTypeNameDoesNotMatch() {
      assertThat(readModelRegistry.query(map("value.id", value(mockReadModel).get("id"), "typeName", Mocks.word())))
          .isEmpty();
    }

    @Test
    void shouldReturnNoResultsWhenTheAgeIsGreaterThanTheMaxAge() {
      assertThat(readModelRegistry.query(map("value.age", map("$gt", 40)))).isEmpty();
    }

    @Test
    void shouldReturnAllResultsWhenTheAgeIsLessThanOrEqualToTheMaxAge() {
      assertThat(readModelRegistry.query(map("value.age", map("$lte", 40)))).hasSize(initialReadModelsCount + 1);
    }

    @Test
    void shouldReturnAllResultsSortedByAge() {
      List<Map<String, Object>> result = readModelRegistry.query(map(), map("age", "DESC"), 0, null, null);
      assertThat(result).hasSize(initialReadModelsCount + 1);
      List<Map<String, Object>> expected = new ArrayList<>(result);
      expected.sort(Comparator.comparing((Map<String, Object> e) -> (Integer) value(e).get("age")).reversed());
      assertThat(result).extracting(e -> value(e).get("age")).isEqualTo(expected.stream().map(e -> value(e).get("age"))
          .toList());
    }

    @Test
    void shouldReturnAllResultsSortedByAgeAndId() {
      List<Map<String, Object>> result = readModelRegistry.query(map(), map("age", "DESC", "id", "DESC"), 0, null,
          null);
      assertThat(result).hasSize(initialReadModelsCount + 1);
      for (int i = 1; i < result.size(); i++) {
        Map<String, Object> previous = value(result.get(i - 1));
        Map<String, Object> current = value(result.get(i));
        int previousAge = (Integer) previous.get("age");
        int currentAge = (Integer) current.get("age");
        assertThat(previousAge).isGreaterThanOrEqualTo(currentAge);
        if (previousAge == currentAge) {
          assertThat((String) previous.get("id")).isGreaterThan((String) current.get("id"));
        }
      }
    }

    @Test
    void shouldReturnOneResultWhenTheAgeIsLessThanOrEqualToTheMaxAgeForTheType() {
      assertThat(readModelRegistry.query(map("value.age", map("$lte", 40), "typeName", mockReadModel.get("typeName"))))
          .hasSize(1);
    }

    @Test
    void shouldReturnSomeResultsWhenTheAgeIsInARangeWithAnAnd() {
      // ages are random in [0, 40]; make sure at least two are in [1, 40], as the TypeScript test silently assumes
      readModelRegistry.store(map("typeName", "Extra", "value", map("id", "e1", "age", 5, "boosterMetadata",
          map("version", 1))), null);
      List<Map<String, Object>> result = readModelRegistry.query(map("$and", list(map("value.age", map("$lte", 40)),
          map("value.age", map("$gte", 1)))));
      assertThat(result.size()).isGreaterThan(1).isLessThanOrEqualTo(initialReadModelsCount + 2);
    }

    @Test
    void shouldReturnOneResultWhenSearchingWithAString() {
      List<Map<String, Object>> result =
          readModelRegistry.query(map("value.foo", value(mockReadModel).get("foo"), "typeName", mockReadModel.get("typeName")));
      assertThat(result).hasSize(1);
      assertIncludes(result.get(0), mockReadModel);
    }

    @Test
    void shouldReturnOneResultWhenSearchingWithARegExp() {
      String foo = (String) value(mockReadModel).get("foo");
      List<Map<String, Object>> result = readModelRegistry.query(map("value.foo", new JsRegExp(foo.substring(0, 4)),
          "typeName", mockReadModel.get("typeName")));
      assertThat(result).hasSize(1);
      assertIncludes(result.get(0), mockReadModel);
    }

    @Test
    void shouldReturnNMinusOneResultsWhenSearchingWithAStringAndNot() {
      List<Map<String, Object>> result = readModelRegistry.query(map("$not", map("value.foo",
          value(mockReadModel).get("foo"))));
      assertThat(result).hasSize(initialReadModelsCount);
      assertThat(result).noneSatisfy(r -> assertIncludes(r, mockReadModel));
    }

    @Test
    void shouldReturnOnlyProjectedFields() {
      List<Map<String, Object>> result = readModelRegistry.query(map("value", value(mockReadModel), "typeName",
          mockReadModel.get("typeName")), null, 0, null, List.of("id", "age"));
      assertThat(result).hasSize(1);
      assertThat(result.get(0).get("value"))
          .isEqualTo(map("id", value(mockReadModel).get("id"), "age", value(mockReadModel).get("age")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnOnlyProjectedFieldsWithArrayFields() {
      List<Map<String, Object>> result = readModelRegistry.query(map("value", value(mockReadModel), "typeName",
          mockReadModel.get("typeName")), null, 0, null, List.of("id", "age", "arr[].id", "prop.items[].name"));
      assertThat(result).hasSize(1);
      Map<String, Object> source = value(mockReadModel);
      List<Object> arr = ((List<Map<String, Object>>) source.get("arr")).stream()
          .map(item -> (Object) map("id", item.get("id"))).toList();
      List<Object> items = ((List<Map<String, Object>>) ((Map<String, Object>) source.get("prop")).get("items"))
          .stream().map(item -> (Object) map("name", item.get("name"))).toList();
      assertThat(result.get(0).get("value")).isEqualTo(map("id", source.get("id"), "age", source.get("age"),
          "arr", arr, "prop", map("items", items)));
    }

    @Test
    void shouldReturnOnlyProjectedFieldsForComplexReadModels() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Map<String, Object> complexReadModel = map("typeName", Mocks.word(), "value", map(
          "id", Mocks.uuid(),
          "x", map("arr", list(map("y", Mocks.word(), "z", random.nextInt(1000)))),
          "foo", map("bar", map(
              "items", list(map("id", Mocks.uuid(), "name", Mocks.word())),
              "baz", map("items", list(map("id", Mocks.uuid(), "name", Mocks.word()))))),
          "arr", list(map("id", Mocks.uuid(), "subArr", list(map("id", Mocks.uuid(), "name", Mocks.word())))),
          "boosterMetadata", map("version", 1, "schemaVersion", 1)));
      readModelRegistry.store(complexReadModel, 1L);
      List<Map<String, Object>> result = readModelRegistry.query(map("value", value(complexReadModel), "typeName",
          complexReadModel.get("typeName")), null, 0, null,
          List.of("id", "x.arr[].z", "foo.bar.items[].id", "foo.bar.baz.items[].id", "arr[].subArr[].id", "arr[].id"));
      assertThat(result).hasSize(1);
      Map<String, Object> source = value(complexReadModel);
      Map<String, Object> x = (Map<String, Object>) source.get("x");
      Map<String, Object> bar = (Map<String, Object>) ((Map<String, Object>) source.get("foo")).get("bar");
      Map<String, Object> arr0 = ((List<Map<String, Object>>) source.get("arr")).get(0);
      assertThat(result.get(0).get("value")).isEqualTo(map(
          "id", source.get("id"),
          "x", map("arr", list(map("z", ((List<Map<String, Object>>) x.get("arr")).get(0).get("z")))),
          "foo", map("bar", map(
              "items", list(map("id", ((List<Map<String, Object>>) bar.get("items")).get(0).get("id"))),
              "baz", map("items", list(map("id", ((List<Map<String, Object>>) ((Map<String, Object>) bar.get("baz"))
                  .get("items")).get(0).get("id")))))),
          "arr", list(map("id", arr0.get("id"), "subArr", list(map("id",
              ((List<Map<String, Object>>) arr0.get("subArr")).get(0).get("id")))))));
    }
  }

  @Nested
  class DeleteById {
    @Test
    void shouldDeleteReadModelsById() {
      Datastore datastore = mock(Datastore.class);
      ReadModelRegistry registry = new ReadModelRegistry(datastore);
      when(datastore.isLoaded()).thenReturn(true);
      Map<String, Object> envelope = Mocks.readModelEnvelope();
      value(envelope).put("id", "1");
      registry.store(envelope, 1L);
      registry.deleteById("1", (String) envelope.get("typeName"));
      verify(datastore).remove(map("typeName", envelope.get("typeName"), "value.id", "1"), false);
    }

    @Test
    void removesAtMostOneDocument() {
      readModelRegistry.store(map("typeName", "T", "value", map("id", "1", "boosterMetadata", map("version", 1))), null);
      readModelRegistry.store(map("typeName", "T", "value", map("id", "2", "boosterMetadata", map("version", 1))), null);
      assertThat(readModelRegistry.deleteById(com.boostercloud.framework.types.concepts.UUID.of("1"), "T")).isEqualTo(1);
      assertThat(readModelRegistry.deleteById("1", "T")).isZero();
      assertThat(readModelRegistry.query(map("typeName", "T"))).hasSize(1);
    }
  }

  @Nested
  class TheStoreMethod {
    @Test
    void shouldUpdateReadModelsWithACompareAndSwap() {
      Datastore datastore = mock(Datastore.class);
      when(datastore.isLoaded()).thenReturn(true);
      when(datastore.update(any(), any(), any())).thenReturn(new UpdateResult(1, null, false));
      ReadModelRegistry registry = new ReadModelRegistry(datastore);
      Map<String, Object> readModel = Mocks.readModelEnvelope();
      ((Map<String, Object>) value(readModel).get("boosterMetadata")).put("version", 2);
      registry.store(readModel, 2L);
      Map<String, Object> expectedDocument = new java.util.LinkedHashMap<>(readModel);
      expectedDocument.put("uniqueKey", readModel.get("typeName") + "_" + value(readModel).get("id") + "_2");
      verify(datastore).update(map("typeName", readModel.get("typeName"), "value.id", value(readModel).get("id"),
          "value.boosterMetadata.version", 2L), expectedDocument, new UpdateOptions(false, false, true));
      assertThat(readModel).doesNotContainKey("uniqueKey");
    }

    @Test
    void shouldThrowWhenNothingCanBeUpdated() {
      Map<String, Object> readModel = map("value", map("id", Mocks.uuid()), "typeName", Mocks.word());
      assertThatThrownBy(() -> readModelRegistry.store(readModel, 1L))
          .isInstanceOf(NedbError.class)
          .hasMessage("Can't update readModel {\"value\":{\"id\":\"" + value(readModel).get("id") + "\"},\"typeName\":\""
              + readModel.get("typeName") + "\",\"uniqueKey\":\"" + readModel.get("typeName") + "_"
              + value(readModel).get("id") + "_undefined\"} with expectedCurrentVersion = 1 . Optimistic concurrency "
              + "error")
          .extracting(e -> ((NedbError) e).errorType()).isEqualTo(ReadModelRegistry.UNIQUE_VIOLATED_ERROR_TYPE);
    }

    @Test
    void optimisticConcurrencyThroughVersions() {
      Map<String, Object> v1 = map("typeName", "T", "value", map("id", "a", "n", 1, "boosterMetadata",
          map("version", 1, "schemaVersion", 1)));
      readModelRegistry.store(v1, null);
      assertThatThrownBy(() -> readModelRegistry.store(v1, null))
          .isInstanceOf(NedbError.class).hasMessage("Can't insert key \"T_a_1\", it violates the unique constraint");
      Map<String, Object> v2 = map("typeName", "T", "value", map("id", "a", "n", 2, "boosterMetadata",
          map("version", 2, "schemaVersion", 1)));
      assertThatThrownBy(() -> readModelRegistry.store(v2, null)).hasMessageEndingWith(
          "with expectedCurrentVersion = undefined . Optimistic concurrency error");
      readModelRegistry.store(v2, 1L);
      assertThatThrownBy(() -> readModelRegistry.store(v2, 1L)).isInstanceOf(NedbError.class);
      assertThat(readModelRegistry.query(map("typeName", "T"))).singleElement()
          .satisfies(doc -> assertThat(doc).containsEntry("uniqueKey", "T_a_2"));
    }

    @Test
    void concurrentWritersOfTheSameVersionOnlyOneWins() throws Exception {
      readModelRegistry.store(map("typeName", "T", "value", map("id", "a", "boosterMetadata", map("version", 1))), null);
      List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
      try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        for (int i = 0; i < 20; i++) {
          int writer = i;
          executor.submit(() -> {
            try {
              readModelRegistry.store(map("typeName", "T", "value", map("id", "a", "writer", writer,
                  "boosterMetadata", map("version", 2))), 1L);
            } catch (NedbError e) {
              failures.add(e);
            }
          });
        }
      }
      assertThat(failures).hasSize(19);
    }

    /** Java-only: TypeScript inserted a second document, because the unique key of the first one had become T_a_2. */
    @Test
    void aVersion1IsRejectedOnceTheReadModelHasMovedToALaterVersion() {
      readModelRegistry.store(map("typeName", "T", "value", map("id", "a", "n", 1, "boosterMetadata",
          map("version", 1))), null);
      readModelRegistry.store(map("typeName", "T", "value", map("id", "a", "n", 2, "boosterMetadata",
          map("version", 2))), 1L);

      assertThatThrownBy(() -> readModelRegistry.store(map("typeName", "T", "value", map("id", "a", "n", 3,
          "boosterMetadata", map("version", 1))), null))
          .isInstanceOf(NedbError.class)
          .hasMessage("Can't insert key \"T_a_1\", it violates the unique constraint")
          .extracting(e -> ((NedbError) e).errorType()).isEqualTo(ReadModelRegistry.UNIQUE_VIOLATED_ERROR_TYPE);
      assertThat(readModelRegistry.query(map("typeName", "T"))).singleElement()
          .satisfies(doc -> assertThat(doc).containsEntry("uniqueKey", "T_a_2"));
      // another type or another id is still inserted, and so is a version 1 next to a document without version
      readModelRegistry.store(map("typeName", "U", "value", map("id", "a", "boosterMetadata", map("version", 1))), null);
      readModelRegistry.store(map("typeName", "T", "value", map("id", "b", "boosterMetadata", map("version", 1))), null);
      readModelRegistry.readModels().insert(map("typeName", "T", "value", map("id", "c")));
      readModelRegistry.store(map("typeName", "T", "value", map("id", "c", "boosterMetadata", map("version", 1))), null);
      assertThat(readModelRegistry.query(map())).hasSize(5);
    }

    /**
     * Java-only: concurrent projections of a new read model, each retried like core's optimistic concurrency (fetch,
     * project, store the next version, retry on a violation), leave one document whose version counts every
     * projection once.
     */
    @Test
    void concurrentProjectionsOfANewReadModelLeaveOneDocumentWithEveryVersion() throws Exception {
      int projections = 8;
      for (int round = 0; round < 5; round++) {
        String id = "rm-" + round;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
          for (int i = 0; i < projections; i++) {
            long firstProjectionNanos = i * 4_000_000L;
            executor.submit(() -> {
              boolean first = true;
              while (true) {
                List<Map<String, Object>> current = readModelRegistry.query(map("typeName", "T", "value.id", id));
                long version = current.isEmpty() ? 0L
                    : ((Number) ((Map<?, ?>) value(current.get(0)).get("boosterMetadata")).get("version")).longValue();
                // The projection runs between the fetch and the store. The first ones are staggered by more than a
                // flushed write, so that later projections that found no read model store their version 1 after the
                // stored one has been updated
                LockSupport.parkNanos(first ? firstProjectionNanos : ThreadLocalRandom.current().nextLong(50_000));
                first = false;
                try {
                  readModelRegistry.store(map("typeName", "T", "value", map("id", id, "boosterMetadata",
                      map("version", version + 1))), version);
                  return null;
                } catch (NedbError e) {
                  // optimistic concurrency: fetch again and retry
                }
              }
            });
          }
        }
        assertThat(readModelRegistry.query(map("typeName", "T", "value.id", id))).singleElement()
            .satisfies(doc -> assertThat(doc).containsEntry("uniqueKey", "T_" + id + "_" + projections));
      }
    }
  }

  @Nested
  class SortAndProjectionHelpers {
    @Test
    void toLocalSortFor() {
      assertThat(readModelRegistry.toLocalSortFor(null)).isNull();
      assertThat(readModelRegistry.toLocalSortFor(map())).isNull();
      assertThat(readModelRegistry.toLocalSortFor(map("age", "DESC", "id", "ASC", "a", map("b", "asc"))))
          .isEqualTo(map("value.age", -1, "value.id", 1, "value.a.b", -1));
      assertThat(readModelRegistry.toLocalSortFor(map("a", map(), "b", com.boostercloud.framework.types.searcher
          .SortDirection.ASC))).isEqualTo(map("value.b", 1));
    }

    @Test
    void filterFieldsKeepsTheTypeScriptQuirks() {
      Map<String, Object> source = map("id", "root", "arr", list(map("id", "a1", "x", 1)), "zero", 0, "nil", null,
          "plainArr", list(map("id", 1)));
      // the remaining segments are also applied to the enclosing object after an array segment
      assertThat(readModelRegistry.filterFields(source, List.of("arr[].x"))).isEqualTo(map("arr", list(map("x", 1))));
      assertThat(readModelRegistry.filterFields(source, List.of("arr[].id"))).isEqualTo(map("arr", list(map("id", "a1")),
          "id", "root"));
      // an array reached without [] gives an empty array
      assertThat(readModelRegistry.filterFields(source, List.of("plainArr.id"))).isEqualTo(map("plainArr", list()));
      assertThat(readModelRegistry.filterFields(source, List.of("zero", "nil", "missing", "zero.x")))
          .isEqualTo(map("zero", 0, "nil", null));
      assertThat(readModelRegistry.filterFields(source, List.of("arr[]"))).isEqualTo(map("arr", source.get("arr")));
      assertThat(readModelRegistry.filterFields(source, List.of("id[].x"))).isEqualTo(map());
      assertThatThrownBy(() -> readModelRegistry.filterFields(map("arr", list((Object) null)), List.of("arr[].x")))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cannot read properties of null");
    }
  }

  @Test
  void anyDatastoreFailurePropagates() {
    Datastore datastore = mock(Datastore.class);
    when(datastore.isLoaded()).thenReturn(true);
    when(datastore.insert(any(Map.class))).thenThrow(new NedbError("boom"));
    ReadModelRegistry registry = new ReadModelRegistry(datastore);
    assertThatThrownBy(() -> registry.store(Mocks.readModelEnvelope(), 1L)).hasMessage("boom");
    verify(datastore, org.mockito.Mockito.never()).remove(any(), anyBoolean());
  }
}
