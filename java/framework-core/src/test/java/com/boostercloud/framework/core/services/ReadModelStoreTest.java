package com.boostercloud.framework.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.BoosterMetadata;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.GlobalErrorHandlerMetadata;
import com.boostercloud.framework.types.concepts.JoinKey;
import com.boostercloud.framework.types.concepts.ProjectionInfo;
import com.boostercloud.framework.types.concepts.ProjectionInfoReason;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelAction;
import com.boostercloud.framework.types.concepts.ReadModelJoinKeyFunction;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import com.boostercloud.framework.types.errors.ProjectionGlobalError;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

/** Port of {@code test/services/read-model-store.test.ts}, plus the behaviour the TypeScript tests leave uncovered. */
class ReadModelStoreTest extends BoosterTestSupport {

  // ---------------------------------------------------------------------------------------------------------------
  // Fixtures (the TypeScript classes)
  // ---------------------------------------------------------------------------------------------------------------

  static final List<List<Object>> CALLS = Collections.synchronizedList(new ArrayList<>());
  static final AtomicInteger ENTITY_METHOD_CALLS = new AtomicInteger();
  static final AtomicInteger READ_MODEL_METHOD_CALLS = new AtomicInteger();

  private static void record(String projection, Object... arguments) {
    List<Object> call = new ArrayList<>();
    call.add(projection);
    call.addAll(Arrays.asList(arguments));
    CALLS.add(call);
  }

  private static List<List<Object>> callsOf(String projection) {
    synchronized (CALLS) {
      return CALLS.stream().filter(call -> call.get(0).equals(projection)).map(call -> call.subList(1, call.size())).toList();
    }
  }

  public record AnImportantEntity(UUID id, UUID someKey, int count) {
    public String getPrefixedKey(String prefix) {
      ENTITY_METHOD_CALLS.incrementAndGet();
      return prefix + "-" + someKey;
    }
  }

  public record AnImportantEntityWithArray(UUID id, List<UUID> someKey, int count) {}

  public record AnEntity(UUID id, UUID someKey, int count) {}

  private static Map<String, Object> projected(Object id, String kind, int count) {
    Map<String, Object> readModel = new LinkedHashMap<>();
    readModel.put("id", id);
    readModel.put("kind", kind);
    readModel.put("count", count);
    return readModel;
  }

  public record SomeReadModel(UUID id, String kind, int count, BoosterMetadata boosterMetadata) {
    public static Object someObserver(AnImportantEntity entity, SomeReadModel obj) {
      record("someObserver", entity, obj);
      int count = (obj == null ? 0 : obj.count()) + entity.count();
      return projected(entity.someKey(), "some", count);
    }

    public static Object someObserverArray(AnImportantEntityWithArray entity, UUID readModelID, SomeReadModel obj) {
      record("someObserverArray", entity, readModelID, obj);
      int count = (obj == null ? 0 : obj.count()) + entity.count();
      return projected(readModelID, "some", count);
    }

    public UUID getId() {
      READ_MODEL_METHOD_CALLS.incrementAndGet();
      return id;
    }

    public static ProjectionResult<SomeReadModel> projectionThatCallsReadModelMethod(AnEntity entity,
        SomeReadModel currentReadModel) {
      currentReadModel.getId();
      return ReadModelAction.NOTHING;
    }

    public static ProjectionResult<SomeReadModel> projectionThatCallsEntityMethod(AnImportantEntity entity,
        SomeReadModel currentReadModel) {
      entity.getPrefixedKey("a prefix");
      return ProjectionResult.nothing();
    }
  }

  public record AnotherReadModel(UUID id, int count) {
    public static Object anotherObserver(AnImportantEntity entity, AnotherReadModel obj) {
      record("anotherObserver", entity, obj);
      int count = (obj == null ? 0 : obj.count()) + entity.count();
      return projected(entity.someKey(), "another", count);
    }
  }

  private static final String EPOCH = "1970-01-01T00:00:00.000Z";

  private BoosterConfig config;
  private ProviderReadModelsLibrary readModels;
  private final List<ProjectionMetadata> importantEntityProjections = List.of(
      new ProjectionMetadata(SomeReadModel.class, "someObserver", JoinKey.property("someKey")),
      new ProjectionMetadata(SomeReadModel.class, "projectionThatCallsEntityMethod", JoinKey.property("someKey")),
      new ProjectionMetadata(AnotherReadModel.class, "anotherObserver", JoinKey.property("someKey")));

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    CALLS.clear();
    ENTITY_METHOD_CALLS.set(0);
    READ_MODEL_METHOD_CALLS.set(0);
    Dates.useClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    readModels = mock(ProviderReadModelsLibrary.class);
    when(provider.readModels()).thenReturn(readModels);
    config.setProvider(provider);
    for (Class<?> entity : List.of(AnImportantEntity.class, AnEntity.class, AnImportantEntityWithArray.class)) {
      config.entities.put(entity.getSimpleName(), new EntityMetadata(entity, BoosterAuthorizer.authorizeRoles(List.of())));
    }
    for (Class<?> readModel : List.of(SomeReadModel.class, AnotherReadModel.class)) {
      config.readModels.put(readModel.getSimpleName(),
          new ReadModelMetadata(readModel, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
    }
    config.projections.put("AnImportantEntity", new ArrayList<>(importantEntityProjections));
    config.projections.put("AnImportantEntityWithArray", new ArrayList<>(List.of(
        new ProjectionMetadata(SomeReadModel.class, "someObserverArray", JoinKey.property("someKey")))));
    config.projections.put("AnEntity", new ArrayList<>(List.of(
        new ProjectionMetadata(SomeReadModel.class, "projectionThatCallsReadModelMethod", JoinKey.property("someKey")))));
    searchReturns(List.of());
  }

  private static EntitySnapshotEnvelope entitySnapshotEnvelopeFor(String entityName) {
    Object someKeyValue = "joinColumnID";
    if (entityName.equals("AnImportantEntityWithArray")) {
      someKeyValue = List.of("joinColumnID", "anotherJoinColumnID");
    }
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", "importantEntityID");
    value.put("someKey", someKeyValue);
    value.put("count", 123);
    String now = Dates.nowIso();
    return EntitySnapshotEnvelope.builder().version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of("42"))
        .entityTypeName(entityName).value(value).requestID(UUID.of("whatever")).typeName(entityName).createdAt(now)
        .persistedAt(now).snapshottedEventCreatedAt(now).build();
  }

  private static final AnImportantEntity AN_ENTITY_INSTANCE =
      new AnImportantEntity(UUID.of("importantEntityID"), UUID.of("joinColumnID"), 123);

  private void searchReturns(List<Map<String, Object>> documents) {
    when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> ReadModelListResult.of(documents));
  }

  private static Map<String, Object> boosterMetadata(long version, String entityName, String projectionMethod) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("version", version);
    metadata.put("schemaVersion", 1);
    metadata.put("lastUpdateAt", EPOCH);
    Map<String, Object> lastProjectionInfo = new LinkedHashMap<>();
    lastProjectionInfo.put("entityId", "importantEntityID");
    lastProjectionInfo.put("entityName", entityName);
    lastProjectionInfo.put("entityUpdatedAt", EPOCH);
    lastProjectionInfo.put("projectionMethod", projectionMethod);
    metadata.put("lastProjectionInfo", lastProjectionInfo);
    return metadata;
  }

  private static Map<String, Object> stored(String id, String kind, int count, Map<String, Object> boosterMetadata) {
    Map<String, Object> document = projected(id, kind, count);
    document.put("boosterMetadata", boosterMetadata);
    return document;
  }

  private static Map<String, Object> existing(String id, String kind, int count, long version, String entityName,
      String projectionMethod) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("version", version);
    metadata.put("lastUpdateAt", EPOCH);
    metadata.put("lastProjectionInfo", Map.of("entityId", "importantEntityID", "entityName", entityName,
        "entityUpdatedAt", EPOCH, "projectionMethod", projectionMethod));
    return stored(id, kind, count, metadata);
  }

  private List<List<Object>> storeCalls(String readModelName) {
    List<List<Object>> calls = new ArrayList<>();
    for (Invocation invocation : mockingDetails(readModels).getInvocations()) {
      if (invocation.getMethod().getName().equals("store") && invocation.getArgument(1).equals(readModelName)) {
        calls.add(Arrays.asList(invocation.getArguments()));
      }
    }
    return calls;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // the `project` method
  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class Project {
    @Test
    void returnsWithoutErrorsAndWithoutPerformingAnyActionsWhenTheEntityHasNoProjections() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      EntitySnapshotEnvelope entitySnapshotWithNoProjections = EntitySnapshotEnvelope.builder().version(1)
          .entityID(UUID.of("42")).entityTypeName("AConceptWithoutProjections").value(Map.of())
          .typeName("AnImportantEntity").createdAt(Dates.nowIso()).build();

      readModelStore.project(entitySnapshotWithNoProjections);

      verify(readModels, never()).store(any(), any(), any(), any());
      verify(readModelStore, never()).fetchReadModelDocument(any(), any(), any());
      verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deletesTheAssociatedReadModelWhenTheProjectionReturnsDelete() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn((ReadModelStore.ProjectionFunction) arguments -> ReadModelAction.DELETE)
          .when(readModelStore).getProjectionFunction(any());

      readModelStore.project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      verify(readModels, never()).store(any(), any(), any(), any());
      verify(readModels, times(3)).delete(eq(config), any(), isNull());
      verify(readModels, times(3)).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresTheReadModelWhenTheProjectionReturnsNothing() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn((ReadModelStore.ProjectionFunction) arguments -> ReadModelAction.NOTHING)
          .when(readModelStore).getProjectionFunction(any());

      readModelStore.project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      verify(readModels, never()).store(any(), any(), any(), any());
      verify(readModels, never()).delete(any(), any(), any());
      verify(readModels, times(3)).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void createsNewInstancesOfTheReadModelsWhenTheyDoNotExist() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn(java.util.Optional.empty()).when(readModelStore).fetchReadModelDocument(any(), any(), any());

      readModelStore.project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(callsOf("someObserver")).containsExactly(Arrays.asList(AN_ENTITY_INSTANCE, null));
      assertThat(callsOf("anotherObserver")).containsExactly(Arrays.asList(AN_ENTITY_INSTANCE, null));
      assertThat(storeCalls("SomeReadModel")).containsExactly(List.of(config, "SomeReadModel",
          stored("joinColumnID", "some", 123, boosterMetadata(1, "AnImportantEntity", "SomeReadModel.someObserver")), 0L));
      assertThat(storeCalls("AnotherReadModel")).containsExactly(List.of(config, "AnotherReadModel",
          stored("joinColumnID", "another", 123, boosterMetadata(1, "AnImportantEntity", "AnotherReadModel.anotherObserver")),
          0L));
      verify(readModels, times(2)).store(any(), any(), any(), any());
      verify(readModels, times(2)).search(eq(config), eq("SomeReadModel"), eq(Map.of("id", Map.of("in", List.of("joinColumnID")))),
          eq(Map.of()), isNull(), isNull(), eq(false), isNull());
    }

    @Test
    void updatesTheReadModelWhenItExists() {
      when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
        String className = invocation.getArgument(1);
        if (className.equals("SomeReadModel")) {
          return ReadModelListResult.of(List.of(
              existing("joinColumnID", "some", 77, 10, "AnImportantEntity", "SomeReadModel.someObserver")));
        }
        return ReadModelListResult.of(List.of(
            existing("joinColumnID", "another", 177, 32, "AnImportantEntity", "AnotherReadModel.anotherObserver")));
      });
      ReadModelStore readModelStore = new ReadModelStore(config);

      readModelStore.project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      List<List<Object>> someObserverCalls = callsOf("someObserver");
      assertThat(someObserverCalls).hasSize(1);
      SomeReadModel current = (SomeReadModel) someObserverCalls.get(0).get(1);
      assertThat(someObserverCalls.get(0).get(0)).isEqualTo(AN_ENTITY_INSTANCE);
      assertThat(current.id()).isEqualTo(UUID.of("joinColumnID"));
      assertThat(current.count()).isEqualTo(77);
      assertThat(current.kind()).isEqualTo("some");
      assertThat(current.boosterMetadata().version()).isEqualTo(10);
      assertThat(current.boosterMetadata().lastProjectionInfo().projectionMethod()).isEqualTo("SomeReadModel.someObserver");
      assertThat(callsOf("anotherObserver")).containsExactly(
          Arrays.asList(AN_ENTITY_INSTANCE, new AnotherReadModel(UUID.of("joinColumnID"), 177)));

      Map<String, Object> someMetadata = new LinkedHashMap<>();
      someMetadata.put("version", 11L);
      someMetadata.put("lastUpdateAt", EPOCH);
      someMetadata.put("lastProjectionInfo", Map.of("entityId", "importantEntityID", "entityName", "AnImportantEntity",
          "entityUpdatedAt", EPOCH, "projectionMethod", "SomeReadModel.someObserver"));
      someMetadata.put("schemaVersion", 1);
      assertThat(storeCalls("SomeReadModel")).containsExactly(
          List.of(config, "SomeReadModel", stored("joinColumnID", "some", 200, someMetadata), 10L));
      assertThat(storeCalls("AnotherReadModel")).containsExactly(List.of(config, "AnotherReadModel",
          stored("joinColumnID", "another", 300, boosterMetadata(33, "AnImportantEntity", "AnotherReadModel.anotherObserver")),
          32L));
      verify(readModels, times(2)).store(any(), any(), any(), any());
    }

    @Test
    void theProjectionCanCallInstanceMethodsOfTheEntity() {
      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(ENTITY_METHOD_CALLS.get()).isEqualTo(1);
    }

    @Test
    void theProjectionCanCallInstanceMethodsOfTheReadModel() {
      searchReturns(List.of(Map.of("id", "joinColumnID", "count", 31415)));

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnEntity"));

      assertThat(READ_MODEL_METHOD_CALLS.get()).isEqualTo(1);
    }

    @Test
    void retries5TimesWhenOptimisticConcurrencyFails4Times() {
      AtomicInteger tryNumber = new AtomicInteger(1);
      int expectedTries = 5;
      doAnswer(invocation -> {
        if (invocation.getArgument(1).equals("SomeReadModel") && tryNumber.get() < expectedTries) {
          tryNumber.incrementAndGet();
          throw new OptimisticConcurrencyUnexpectedVersionError("test error");
        }
        return null;
      }).when(readModels).store(any(), any(), any(), any());

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      List<List<Object>> someReadModelStoreCalls = storeCalls("SomeReadModel");
      assertThat(someReadModelStoreCalls).hasSize(expectedTries);
      assertThat(someReadModelStoreCalls).allSatisfy(call -> assertThat(call).isEqualTo(List.of(config, "SomeReadModel",
          stored("joinColumnID", "some", 123, boosterMetadata(1, "AnImportantEntity", "SomeReadModel.someObserver")), 0L)));
    }

    @Test
    void createsNonExistentReadModelsAndUpdatesExistingOnesFromAnArrayJoinKey() {
      searchReturns(List.of(existing("joinColumnID", "some", 77, 10, "AnImportantEntityWithArray",
          "SomeReadModel.someObserverArray")));
      AnImportantEntityWithArray entity = new AnImportantEntityWithArray(UUID.of("importantEntityID"),
          List.of(UUID.of("joinColumnID"), UUID.of("anotherJoinColumnID")), 123);

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntityWithArray"));

      List<List<Object>> calls = callsOf("someObserverArray");
      assertThat(calls).hasSize(2);
      List<Object> existingCall = calls.stream().filter(call -> call.get(1).equals(UUID.of("joinColumnID"))).findFirst().orElseThrow();
      assertThat(existingCall.get(0)).isEqualTo(entity);
      assertThat(((SomeReadModel) existingCall.get(2)).count()).isEqualTo(77);
      assertThat(calls).contains(Arrays.asList(entity, UUID.of("anotherJoinColumnID"), null));

      Map<String, Object> updatedMetadata = new LinkedHashMap<>();
      updatedMetadata.put("version", 11L);
      updatedMetadata.put("lastUpdateAt", EPOCH);
      updatedMetadata.put("lastProjectionInfo", Map.of("entityId", "importantEntityID", "entityName",
          "AnImportantEntityWithArray", "entityUpdatedAt", EPOCH, "projectionMethod", "SomeReadModel.someObserverArray"));
      updatedMetadata.put("schemaVersion", 1);
      assertThat(storeCalls("SomeReadModel")).containsExactlyInAnyOrder(
          List.of(config, "SomeReadModel", stored("joinColumnID", "some", 200, updatedMetadata), 10L),
          List.of(config, "SomeReadModel", stored("anotherJoinColumnID", "some", 123,
              boosterMetadata(1, "AnImportantEntityWithArray", "SomeReadModel.someObserverArray")), 0L));
    }

    @Test
    void retriesAreIndependentForEveryReadModelOfAnArrayJoinKey() {
      AtomicInteger tryNumber = new AtomicInteger(1);
      int expectedAnotherJoinColumnIDTries = 5;
      int expectedJoinColumnIDTries = 1;
      doAnswer(invocation -> {
        Map<String, Object> readModel = invocation.getArgument(2);
        if (invocation.getArgument(1).equals("SomeReadModel") && readModel.get("id").equals("anotherJoinColumnID")
            && tryNumber.get() < expectedAnotherJoinColumnIDTries) {
          tryNumber.incrementAndGet();
          throw new OptimisticConcurrencyUnexpectedVersionError("test error");
        }
        return null;
      }).when(readModels).store(any(), any(), any(), any());

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntityWithArray"));

      List<List<Object>> someReadModelStoreCalls = storeCalls("SomeReadModel");
      assertThat(someReadModelStoreCalls).hasSize(expectedJoinColumnIDTries + expectedAnotherJoinColumnIDTries);
      assertThat(someReadModelStoreCalls.stream().filter(call -> ((Map<?, ?>) call.get(2)).get("id").equals("joinColumnID")))
          .hasSize(expectedJoinColumnIDTries)
          .allSatisfy(call -> assertThat(call.get(3)).isEqualTo(0L));
      assertThat(someReadModelStoreCalls.stream().filter(call -> ((Map<?, ?>) call.get(2)).get("id").equals("anotherJoinColumnID")))
          .hasSize(expectedAnotherJoinColumnIDTries)
          .allSatisfy(call -> assertThat(call.get(3)).isEqualTo(0L));
    }

    @Test
    void appliesTheProjectionsWithTheRightSequenceMetadata() {
      config.readModelSequenceKeys.put("AnotherReadModel", "count");
      EntitySnapshotEnvelope anEntitySnapshot = entitySnapshotEnvelopeFor("AnImportantEntity");
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn(null).when(readModelStore)
          .applyProjectionToReadModel(any(), any(), any(), eq(false), any(), any(), any(), any(), anyInt());

      readModelStore.project(anEntitySnapshot);

      verify(readModelStore, times(3))
          .applyProjectionToReadModel(any(), any(), any(), eq(false), any(), any(), any(), any(), anyInt());
      for (ProjectionMetadata projectionMetadata : importantEntityProjections) {
        SequenceKey sequenceKey = projectionMetadata.type() == AnotherReadModel.class ? SequenceKey.of("count", "123") : null;
        verify(readModelStore).applyProjectionToReadModel(anEntitySnapshot, AN_ENTITY_INSTANCE, projectionMetadata, false,
            null, anEntitySnapshot, UUID.of("joinColumnID"), sequenceKey, 1);
      }
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // the `fetchReadModel` method
  // ---------------------------------------------------------------------------------------------------------------

  @Nested
  class FetchReadModel {
    @Test
    void returnsNothingWhenTheReadModelDoesNotExist() {
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(null);

      assertThat(new ReadModelStore(config).fetchReadModel("SomeReadModel", UUID.of("joinColumnID"), null)).isEmpty();
      verify(readModels, times(1)).fetch(config, "SomeReadModel", UUID.of("joinColumnID"), null);
    }

    @Test
    void returnsNothingWhenTheProviderReturnsAListWithNull() {
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(Arrays.asList((Map<String, Object>) null));

      assertThat(new ReadModelStore(config).fetchReadModel("SomeReadModel", UUID.of("joinColumnID"), null)).isEmpty();
      verify(readModels, times(1)).fetch(config, "SomeReadModel", UUID.of("joinColumnID"), null);
    }

    @Test
    void returnsAnInstanceOfTheCurrentReadModelValueWhenItExists() {
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(List.of(Map.of("id", "joinColumnID", "count", 0)));

      assertThat(new ReadModelStore(config).fetchReadModel("SomeReadModel", UUID.of("joinColumnID"), null))
          .contains(new SomeReadModel(UUID.of("joinColumnID"), null, 0, null));
      verify(readModels, times(1)).fetch(config, "SomeReadModel", UUID.of("joinColumnID"), null);
    }

    @Test
    void passesTheSequenceKeyToTheProvider() {
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(List.of(Map.of("id", "joinColumnID")));
      SequenceKey sequenceKey = SequenceKey.of("time", "now!");

      new ReadModelStore(config).fetchReadModel("SomeReadModel", UUID.of("joinColumnID"), sequenceKey);

      verify(readModels, times(1)).fetch(config, "SomeReadModel", UUID.of("joinColumnID"), sequenceKey);
    }

    @Test
    void doesNotCallTheProviderWithoutId() {
      assertThat(new ReadModelStore(config).fetchReadModel("SomeReadModel", null, null)).isEmpty();
      assertThat(new ReadModelStore(config).fetchReadModel("SomeReadModel", UUID.of(""), null)).isEmpty();
      verify(readModels, never()).fetch(any(), any(), any(), any());
    }

    @Test
    void failsWhenTheProviderReturnsSeveralReadModels() {
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(List.of(Map.of("id", "a"), Map.of("id", "a")));

      assertThatThrownBy(() -> new ReadModelStore(config).fetchReadModel("SomeReadModel", UUID.of("a"), null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Got multiple objects for a request by Id. If this is a sequenced read model you should also specify"
              + " the sequenceKey field.");
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // the `sequenceKeyForProjection` method
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void sequenceKeyForProjectionIsNullWithoutSequenceKey() {
    assertThat(new ReadModelStore(config).sequenceKeyForProjection(AN_ENTITY_INSTANCE,
        new ProjectionMetadata(SomeReadModel.class, "x", JoinKey.property("someKey")))).isNull();
  }

  @Test
  void sequenceKeyForProjectionReadsTheValueFromTheEntity() {
    config.readModelSequenceKeys.put("AnotherReadModel", "count");

    assertThat(new ReadModelStore(config).sequenceKeyForProjection(AN_ENTITY_INSTANCE,
        new ProjectionMetadata(AnotherReadModel.class, "x", JoinKey.property("someKey")))).isEqualTo(SequenceKey.of("count", "123"));
  }

  @Test
  void sequenceKeyForProjectionIsNullWhenTheEntityValueIsFalsy() {
    config.readModelSequenceKeys.put("AnotherReadModel", "count");

    assertThat(new ReadModelStore(config).sequenceKeyForProjection(new AnImportantEntity(UUID.of("1"), UUID.of("k"), 0),
        new ProjectionMetadata(AnotherReadModel.class, "x", JoinKey.property("someKey")))).isNull();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // the `applyProjectionToReadModel` method (TypeScript: pending tests) and the untested behaviour (spec 07 §5.1)
  // ---------------------------------------------------------------------------------------------------------------

  private ProjectionMetadata projection(Class<?> readModel, String method, JoinKey joinKey) {
    return new ProjectionMetadata(readModel, method, joinKey);
  }

  @Nested
  class ApplyProjectionToReadModel {
    @Test
    void deletesTheMigratedReadModelOnDelete() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn((ReadModelStore.ProjectionFunction) arguments -> ReadModelAction.DELETE).when(readModelStore).getProjectionFunction(any());
      Map<String, Object> current = Map.of("id", "a", "count", 1);
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, importantEntityProjections.get(0), false, current,
          envelope, UUID.of("a"), null, 1);

      verify(readModels).delete(config, "SomeReadModel", current);
      verify(readModels, never()).store(any(), any(), any(), any());
    }

    @Test
    void doesNothingOnNothing() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn((ReadModelStore.ProjectionFunction) arguments -> ReadModelAction.NOTHING).when(readModelStore).getProjectionFunction(any());
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, importantEntityProjections.get(0), false,
          Map.of("id", "a"), envelope, UUID.of("a"), null, 1);

      verify(readModels, never()).delete(any(), any(), any());
      verify(readModels, never()).store(any(), any(), any(), any());
    }

    @Test
    void refetchesTheReadModelWithoutSequenceKeyFromTheSecondTry() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(List.of(existing("joinColumnID", "some", 5, 7,
          "AnImportantEntity", "SomeReadModel.someObserver")));
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, importantEntityProjections.get(0), false, null,
          envelope, UUID.of("joinColumnID"), null, 2);

      verify(readModelStore).fetchReadModelDocument("SomeReadModel", UUID.of("joinColumnID"), null);
      verify(readModels).store(eq(config), eq("SomeReadModel"), any(), eq(7L));
      assertThat(storeCalls("SomeReadModel").get(0).get(2)).extracting(document -> ((Map<?, ?>) document).get("count"))
          .isEqualTo(128);
    }

    @Test
    void refetchesTheReadModelWithTheSequenceKey() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      when(readModels.fetch(any(), any(), any(), any())).thenReturn(List.of());
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");
      SequenceKey sequenceKey = SequenceKey.of("count", "123");

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, importantEntityProjections.get(0), false, null,
          envelope, UUID.of("joinColumnID"), sequenceKey, 2);

      verify(readModelStore).fetchReadModelDocument("SomeReadModel", UUID.of("joinColumnID"), sequenceKey);
      verify(readModels).fetch(config, "SomeReadModel", UUID.of("joinColumnID"), sequenceKey);
    }

    @Test
    void doesNotFetchOnTheFirstTry() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, importantEntityProjections.get(0), false, null,
          envelope, UUID.of("joinColumnID"), null, 1);

      verify(readModelStore, never()).fetchReadModelDocument(any(), any(), any());
    }

    @Test
    void keepsTheProviderMetadataOfThePreviousDocumentAndStoresProjectionResults() {
      ProjectionMetadata projectionMetadata = projection(AnotherReadModel.class, "ofRecord", JoinKey.property("someKey"));
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn((ReadModelStore.ProjectionFunction) arguments -> ProjectionResult.of(new AnotherReadModel(UUID.of("x"), 9)))
          .when(readModelStore).getProjectionFunction(any());
      Map<String, Object> current = new LinkedHashMap<>();
      current.put("id", "x");
      current.put("boosterMetadata", new LinkedHashMap<>(Map.of("version", 3, "schemaVersion", 1,
          "optimisticConcurrencyValue", "etag-1")));
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, projectionMetadata, false, current, envelope,
          null, null, 1);

      Map<String, Object> document = (Map<String, Object>) storeCalls("AnotherReadModel").get(0).get(2);
      assertThat(document).containsEntry("id", "x").containsEntry("count", 9);
      assertThat((Map<String, Object>) document.get("boosterMetadata"))
          .containsEntry("version", 4L)
          .containsEntry("schemaVersion", 1)
          .containsEntry("optimisticConcurrencyValue", "etag-1")
          .containsEntry("lastUpdateAt", EPOCH)
          .containsEntry("lastProjectionInfo", Map.of("entityId", "importantEntityID", "entityName", "AnImportantEntity",
              "entityUpdatedAt", EPOCH, "projectionMethod", "AnotherReadModel.ofRecord"));
      verify(readModels).store(config, "AnotherReadModel", document, 3L);
    }

    @Test
    void aProjectionReturningNullFails() {
      ReadModelStore readModelStore = spy(new ReadModelStore(config));
      doReturn((ReadModelStore.ProjectionFunction) arguments -> null).when(readModelStore).getProjectionFunction(any());
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      assertThatThrownBy(() -> readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE,
          importantEntityProjections.get(0), false, null, envelope, UUID.of("a"), null, 1))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageStartingWith("The projection SomeReadModel.someObserver returned null");
    }

    @Test
    void migratesTheCurrentReadModelBeforeProjecting() {
      Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
      migrations.put(2, new SchemaMigrationMetadata(AnotherReadModelMigration.class, "doubleCount", 2, AnotherReadModel.class,
          AnotherReadModel.class));
      config.schemaMigrations.put("AnotherReadModel", migrations);
      ReadModelStore readModelStore = new ReadModelStore(config);
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");
      Map<String, Object> current = new LinkedHashMap<>(Map.of("id", "joinColumnID", "count", 10,
          "boosterMetadata", Map.of("version", 2, "schemaVersion", 1)));

      readModelStore.applyProjectionToReadModel(envelope, AN_ENTITY_INSTANCE, importantEntityProjections.get(2), false,
          current, envelope, UUID.of("joinColumnID"), null, 1);

      assertThat(callsOf("anotherObserver")).containsExactly(
          Arrays.asList(AN_ENTITY_INSTANCE, new AnotherReadModel(UUID.of("joinColumnID"), 20)));
      Map<String, Object> document = (Map<String, Object>) storeCalls("AnotherReadModel").get(0).get(2);
      assertThat(document).containsEntry("count", 143);
      assertThat((Map<String, Object>) document.get("boosterMetadata")).containsEntry("version", 3L).containsEntry("schemaVersion", 2);
      verify(readModels).store(config, "AnotherReadModel", document, 2L);
    }
  }

  /** Doubles the count (schema migration fixture). */
  public static class AnotherReadModelMigration {
    public AnotherReadModel doubleCount(AnotherReadModel old) {
      return new AnotherReadModel(old.id(), old.count() * 2);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Un-projections
  // ---------------------------------------------------------------------------------------------------------------

  /** A read model with a projection and its un-projection. */
  public record DeletableReadModel(UUID id, int count) {
    public static Object project(AnImportantEntity entity, DeletableReadModel current, ProjectionInfo info) {
      record("project", entity, current, info);
      return new DeletableReadModel(entity.someKey(), entity.count());
    }

    public static ProjectionResult<DeletableReadModel> unProject(AnImportantEntity entity, DeletableReadModel current,
        ProjectionInfo info) {
      record("unProject", entity, current, info);
      return ReadModelAction.DELETE;
    }
  }

  @Nested
  class UnProjections {
    @BeforeEach
    void registerDeletable() {
      config.readModels.put("DeletableReadModel",
          new ReadModelMetadata(DeletableReadModel.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
    }

    @Test
    void runsTheUnProjectionsWithEntityDeleted() {
      JoinKey joinKey = JoinKey.property("someKey");
      config.projections.put("AnImportantEntity", new ArrayList<>(List.of(projection(DeletableReadModel.class, "project", joinKey))));
      config.unProjections.put("AnImportantEntity", new ArrayList<>(List.of(projection(DeletableReadModel.class, "unProject", joinKey))));
      Map<String, Object> current = Map.of("id", "joinColumnID", "count", 1, "boosterMetadata", Map.of("version", 1));
      searchReturns(List.of(current));

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"), true);

      assertThat(callsOf("project")).isEmpty();
      assertThat(callsOf("unProject")).containsExactly(Arrays.asList(AN_ENTITY_INSTANCE,
          new DeletableReadModel(UUID.of("joinColumnID"), 1), new ProjectionInfo(ProjectionInfoReason.ENTITY_DELETED)));
      verify(readModels).delete(config, "DeletableReadModel", current);
    }

    @Test
    void projectionsReceiveEntityProjected() {
      JoinKey joinKey = JoinKey.property("someKey");
      config.projections.put("AnImportantEntity", new ArrayList<>(List.of(projection(DeletableReadModel.class, "project", joinKey))));

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(callsOf("project")).containsExactly(
          Arrays.asList(AN_ENTITY_INSTANCE, null, new ProjectionInfo(ProjectionInfoReason.ENTITY_PROJECTED)));
    }

    @Test
    void failsWhenTheEntityHasProjectionsButNoUnProjections() {
      ReadModelStore readModelStore = new ReadModelStore(config);

      assertThatThrownBy(() -> readModelStore.project(entitySnapshotEnvelopeFor("AnImportantEntity"), true))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Missing UnProjections for entity AnImportantEntity");
      verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void failsWithTheFirstProjectionWithoutUnProjection() {
      config.unProjections.put("AnImportantEntity", new ArrayList<>(List.of(
          projection(SomeReadModel.class, "someUnProjection", JoinKey.property("someKey")))));

      assertThatThrownBy(() -> new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"), true))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Missing UnProjection for ReadModel AnotherReadModel with joinKey someKey for entity AnImportantEntity");
    }

    @Test
    void anEntityWithoutProjectionsRunsItsUnProjectionsAsTheyAre() {
      config.projections.remove("AnImportantEntity");
      config.unProjections.put("AnImportantEntity", new ArrayList<>(List.of(
          projection(DeletableReadModel.class, "unProject", JoinKey.property("someKey")))));

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"), true);

      assertThat(callsOf("unProject")).hasSize(1);
      verify(readModels).delete(config, "DeletableReadModel", null);
    }

    @Test
    void anEntityWithoutProjectionsNorUnProjectionsIsSkipped() {
      config.projections.remove("AnImportantEntity");

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"), true);

      verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Join-key functions
  // ---------------------------------------------------------------------------------------------------------------

  /** Selects the read models owned by the entity. */
  public static class ByOwner implements ReadModelJoinKeyFunction<AnImportantEntity> {
    static final AtomicReference<Map<String, Object>> FILTER = new AtomicReference<>();

    @Override
    public Map<String, Object> joinKey(AnImportantEntity entity) {
      return FILTER.get();
    }
  }

  /** A read model projected through a join-key function. */
  public record OwnedReadModel(UUID id, int count) {
    public static Object byOwner(AnImportantEntity entity, UUID readModelID, OwnedReadModel current) {
      record("byOwner", entity, readModelID, current);
      return new OwnedReadModel(readModelID == null ? UUID.of("new") : readModelID, entity.count());
    }
  }

  @Nested
  class JoinKeyFunctions {
    @BeforeEach
    void registerOwned() {
      config.readModels.put("OwnedReadModel",
          new ReadModelMetadata(OwnedReadModel.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
      config.projections.put("AnImportantEntity",
          new ArrayList<>(List.of(projection(OwnedReadModel.class, "byOwner", JoinKey.query(new ByOwner())))));
      ByOwner.FILTER.set(Map.of("ownerId", Map.of("eq", "user-1")));
    }

    @Test
    void searchesWithTheFilterOfTheFunctionAndProjectsEveryMatch() {
      searchReturns(List.of(Map.of("id", "a", "count", 1), Map.of("id", "b", "count", 2)));

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      verify(readModels).search(eq(config), eq("OwnedReadModel"), eq(Map.of("ownerId", Map.of("eq", "user-1"))), eq(Map.of()),
          isNull(), isNull(), eq(false), isNull());
      assertThat(callsOf("byOwner")).containsExactlyInAnyOrder(
          Arrays.asList(AN_ENTITY_INSTANCE, UUID.of("a"), new OwnedReadModel(UUID.of("a"), 1)),
          Arrays.asList(AN_ENTITY_INSTANCE, UUID.of("b"), new OwnedReadModel(UUID.of("b"), 2)));
      verify(readModels, times(2)).store(eq(config), eq("OwnedReadModel"), any(), eq(0L));
    }

    @Test
    void projectsOnceWithoutIdNorReadModelWhenNothingMatches() {
      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(callsOf("byOwner")).containsExactly(Arrays.asList(AN_ENTITY_INSTANCE, null, null));
      assertThat(storeCalls("OwnedReadModel")).hasSize(1);
      assertThat(((Map<?, ?>) storeCalls("OwnedReadModel").get(0).get(2)).get("id")).isEqualTo("new");
    }

    @Test
    void aNullFilterSkipsTheSearchButStillProjects() {
      ByOwner.FILTER.set(null);

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
      assertThat(callsOf("byOwner")).containsExactly(Arrays.asList(AN_ENTITY_INSTANCE, null, null));
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Several existing read models, failures and the global error handler
  // ---------------------------------------------------------------------------------------------------------------

  @Test
  void anArrayJoinKeyWithSeveralExistingReadModelsProjectsEveryIdOnce() {
    searchReturns(List.of(Map.of("id", "joinColumnID", "count", 1), Map.of("id", "anotherJoinColumnID", "count", 2)));

    new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntityWithArray"));

    List<List<Object>> calls = callsOf("someObserverArray");
    assertThat(calls).hasSize(2);
    assertThat(calls).allSatisfy(call -> assertThat(call.get(2)).isNotNull());
    assertThat(storeCalls("SomeReadModel")).extracting(call -> (Object) ((Map<?, ?>) call.get(2)).get("count"))
        .containsExactlyInAnyOrder(124, 125);
  }

  @Test
  void aSequencedReadModelProjectsEveryRowOfTheId() {
    config.readModelSequenceKeys.put("SomeReadModel", "count");
    config.projections.put("AnImportantEntity", new ArrayList<>(List.of(importantEntityProjections.get(0))));
    searchReturns(List.of(Map.of("id", "joinColumnID", "count", 1), Map.of("id", "joinColumnID", "count", 2)));

    new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

    assertThat(callsOf("someObserver")).extracting(call -> ((SomeReadModel) call.get(1)).count()).containsExactlyInAnyOrder(1, 2);
  }

  @Test
  void aFailingProjectionDoesNotStopTheOthersAndIsAggregated() {
    config.projections.put("AnImportantEntity", new ArrayList<>(List.of(
        importantEntityProjections.get(0),
        projection(SomeReadModel.class, "doesNotExist", JoinKey.property("someKey")),
        importantEntityProjections.get(2))));

    assertThatThrownBy(() -> new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity")))
        .isInstanceOfSatisfying(PromisesError.class, error -> {
          assertThat(error.failedReasons()).hasSize(1);
          assertThat(error.failedReasons().get(0)).isInstanceOf(PromisesError.class);
          Throwable reason = ((PromisesError) error.failedReasons().get(0)).failedReasons().get(0);
          assertThat(reason).isInstanceOf(IllegalStateException.class)
              .hasMessageStartingWith("SomeReadModel.doesNotExist is not a function");
        });
    assertThat(storeCalls("SomeReadModel")).hasSize(1);
    assertThat(storeCalls("AnotherReadModel")).hasSize(1);
  }

  @Test
  void retriesStopAfter1000Attempts() {
    config.projections.put("AnImportantEntity", new ArrayList<>(List.of(importantEntityProjections.get(0))));
    doAnswer(invocation -> {
      throw new OptimisticConcurrencyUnexpectedVersionError("always");
    }).when(readModels).store(any(), any(), any(), any());

    assertThatThrownBy(() -> new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity")))
        .isInstanceOfSatisfying(PromisesError.class, error -> assertThat(
            ((PromisesError) error.failedReasons().get(0)).failedReasons().get(0))
            .hasMessage("[retryIfError] Reached the maximum number of retries (1000), but still getting the following error:"
                + " OptimisticConcurrencyUnexpectedVersionError: always"));
    assertThat(storeCalls("SomeReadModel")).hasSize(1000);
  }

  @Test
  void anUnregisteredEntityFails() {
    config.entities.remove("AnImportantEntity");

    assertThatThrownBy(() -> new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AnImportantEntity");
  }

  @Test
  void anEmptyJoinKeyIsSkipped() {
    EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntityWithArray").toBuilder()
        .value(Map.of("id", "importantEntityID", "someKey", List.of(), "count", 1)).build();

    new ReadModelStore(config).project(envelope);

    verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
    assertThat(callsOf("someObserverArray")).isEmpty();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Optional join keys (Optional<T> is the Java form of `T | undefined`)
  // ---------------------------------------------------------------------------------------------------------------

  public record AnOptionalKeyEntity(UUID id, Optional<UUID> someKey, int count) {}

  public record AnOptionalArrayKeyEntity(UUID id, Optional<List<UUID>> someKey, int count) {}

  public record OptionalKeyReadModel(UUID id, int count) {
    public static Object byKey(AnOptionalKeyEntity entity, OptionalKeyReadModel current) {
      record("byKey", entity, current);
      return Map.of("id", entity.someKey().orElseThrow(), "count", entity.count());
    }

    public static Object byKeys(AnOptionalArrayKeyEntity entity, UUID readModelID, OptionalKeyReadModel current) {
      record("byKeys", entity, readModelID, current);
      return Map.of("id", readModelID, "count", entity.count());
    }
  }

  @Nested
  class OptionalJoinKeys {
    @BeforeEach
    void registerOptionalKeys() {
      for (Class<?> entity : List.of(AnOptionalKeyEntity.class, AnOptionalArrayKeyEntity.class)) {
        config.entities.put(entity.getSimpleName(), new EntityMetadata(entity, BoosterAuthorizer.authorizeRoles(List.of())));
      }
      config.readModels.put("OptionalKeyReadModel",
          new ReadModelMetadata(OptionalKeyReadModel.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
      config.projections.put("AnOptionalKeyEntity", new ArrayList<>(List.of(
          new ProjectionMetadata(OptionalKeyReadModel.class, "byKey", JoinKey.property("someKey")))));
      config.projections.put("AnOptionalArrayKeyEntity", new ArrayList<>(List.of(
          new ProjectionMetadata(OptionalKeyReadModel.class, "byKeys", JoinKey.property("someKey")))));
    }

    private EntitySnapshotEnvelope snapshot(String entityName, Object someKey) {
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("id", "importantEntityID");
      if (someKey != null) {
        value.put("someKey", someKey);
      }
      value.put("count", 5);
      return entitySnapshotEnvelopeFor(entityName).toBuilder().value(value).build();
    }

    private boolean warned(String text) {
      return mockingDetails(config.logger).getInvocations().stream()
          .filter(invocation -> invocation.getMethod().getName().equals("warn"))
          .anyMatch(invocation -> Arrays.deepToString(invocation.getArguments()).contains(text));
    }

    @Test
    void anEmptyOptionalJoinKeyIsSkippedWithAWarning() {
      new ReadModelStore(config).project(snapshot("AnOptionalKeyEntity", null));

      assertThat(callsOf("byKey")).isEmpty();
      verify(readModels, never()).search(any(), any(), any(), any(), any(), any(), any(), any());
      verify(readModels, never()).store(any(), any(), any(), any());
      assertThat(warned("Couldn't find the joinKey")).isTrue();
    }

    @Test
    void aPresentOptionalJoinKeyIsTheValueInside() {
      new ReadModelStore(config).project(snapshot("AnOptionalKeyEntity", "cust-1"));

      verify(readModels).search(eq(config), eq("OptionalKeyReadModel"),
          eq(Map.of("id", Map.of("in", List.of("cust-1")))), any(), any(), any(), any(), any());
      assertThat(callsOf("byKey")).containsExactly(Arrays.asList(
          new AnOptionalKeyEntity(UUID.of("importantEntityID"), Optional.of(UUID.of("cust-1")), 5), null));
      assertThat(storeCalls("OptionalKeyReadModel")).extracting(call -> (Object) ((Map<?, ?>) call.get(2)).get("id"))
          .containsExactly("cust-1");
    }

    @Test
    void anOptionalListJoinKeyIsAnArrayJoinKey() {
      new ReadModelStore(config).project(snapshot("AnOptionalArrayKeyEntity", List.of("a", "b")));

      assertThat(callsOf("byKeys")).extracting(call -> call.get(1)).containsExactlyInAnyOrder(UUID.of("a"), UUID.of("b"));
      assertThat(storeCalls("OptionalKeyReadModel")).extracting(call -> (Object) ((Map<?, ?>) call.get(2)).get("id"))
          .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void anEmptyOptionalSequenceKeyIsNoSequenceKey() {
      config.readModelSequenceKeys.put("OptionalKeyReadModel", "someKey");
      ProjectionMetadata projection = new ProjectionMetadata(OptionalKeyReadModel.class, "byKey", JoinKey.property("id"));

      assertThat(new ReadModelStore(config).sequenceKeyForProjection(
          new AnOptionalKeyEntity(UUID.of("e"), Optional.empty(), 1), projection)).isNull();
      assertThat(new ReadModelStore(config).sequenceKeyForProjection(
          new AnOptionalKeyEntity(UUID.of("e"), Optional.of(UUID.of("t1")), 1), projection))
          .isEqualTo(SequenceKey.of("someKey", "t1"));
    }
  }

  @Test
  void getReadModelsReturnsInstances() {
    searchReturns(List.of(Map.of("id", "joinColumnID", "count", 3)));

    List<Object> readModelsFound = new ReadModelStore(config).getReadModels(AN_ENTITY_INSTANCE,
        importantEntityProjections.get(2), config.entities.get("AnImportantEntity"));

    assertThat(readModelsFound).containsExactly(new AnotherReadModel(UUID.of("joinColumnID"), 3));
  }

  /** Records projection errors. */
  public static class ErrorHandler {
    static final List<Object[]> ERRORS = Collections.synchronizedList(new ArrayList<>());
    static final AtomicReference<Throwable> RESULT = new AtomicReference<>();

    public static Throwable onProjectionError(Throwable error, EntitySnapshotEnvelope entityEnvelope,
        ProjectionMetadata projectionMetadata, Object entity, Object readModel) {
      ERRORS.add(new Object[] {error, entityEnvelope, projectionMetadata, entity, readModel});
      return RESULT.get();
    }

    public static Throwable onError(Throwable error) {
      return error;
    }
  }

  /** A projection that fails. */
  public record FailingReadModel(UUID id) {
    public static Object fail(AnImportantEntity entity, FailingReadModel current) {
      throw new IllegalArgumentException("projection failure");
    }
  }

  @Nested
  class GlobalErrorHandler {
    @BeforeEach
    void registerHandler() {
      ErrorHandler.ERRORS.clear();
      ErrorHandler.RESULT.set(null);
      config.globalErrorsHandler = new GlobalErrorHandlerMetadata(ErrorHandler.class);
      config.readModels.put("FailingReadModel",
          new ReadModelMetadata(FailingReadModel.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
      config.projections.put("AnImportantEntity",
          new ArrayList<>(List.of(projection(FailingReadModel.class, "fail", JoinKey.property("someKey")))));
    }

    @Test
    void theErrorIsDispatchedOnceAndSwallowedWhenTheHandlerReturnsNull() {
      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(ErrorHandler.ERRORS).hasSize(1);
      Object[] call = ErrorHandler.ERRORS.get(0);
      assertThat((Throwable) call[0]).isInstanceOf(IllegalArgumentException.class).hasMessage("projection failure");
      assertThat(((ProjectionMetadata) call[2]).methodName()).isEqualTo("fail");
      assertThat(call[3]).isEqualTo(AN_ENTITY_INSTANCE);
      assertThat(call[4]).isNull();
      verify(readModels, never()).store(any(), any(), any(), any());
    }

    @Test
    void theErrorReturnedByTheHandlerIsThrown() {
      ErrorHandler.RESULT.set(new IllegalStateException("handled"));

      assertThatThrownBy(() -> new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity")))
          .isInstanceOfSatisfying(PromisesError.class, error -> assertThat(
              ((PromisesError) error.failedReasons().get(0)).failedReasons().get(0)).hasMessage("handled"));
      assertThat(ErrorHandler.ERRORS).hasSize(1);
    }

    @Test
    void withoutHandlerTheOriginalErrorPropagates() {
      config.globalErrorsHandler = null;

      assertThatThrownBy(() -> new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity")))
          .isInstanceOfSatisfying(PromisesError.class, error -> assertThat(
              ((PromisesError) error.failedReasons().get(0)).failedReasons().get(0))
              .isInstanceOf(IllegalArgumentException.class).hasMessage("projection failure"));
    }

    @Test
    void theHandlerReceivesTheMigratedReadModelInstance() {
      searchReturns(List.of(Map.of("id", "joinColumnID")));

      new ReadModelStore(config).project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(ErrorHandler.ERRORS.get(0)[4]).isEqualTo(new FailingReadModel(UUID.of("joinColumnID")));
    }

    @Test
    void optimisticConcurrencyErrorsThrownByTheProjectionAreRetriedToo() {
      config.globalErrorsHandler = null;
      AtomicInteger attempts = new AtomicInteger();
      ReadModelStore readModelStore = new ReadModelStore(config) {
        @Override
        public ProjectionFunction getProjectionFunction(ProjectionMetadata projectionMetadata) {
          return arguments -> {
            if (attempts.incrementAndGet() < 3) {
              throw new OptimisticConcurrencyUnexpectedVersionError("from the projection");
            }
            return ReadModelAction.NOTHING;
          };
        }
      };

      readModelStore.project(entitySnapshotEnvelopeFor("AnImportantEntity"));

      assertThat(attempts.get()).isEqualTo(3);
      verify(readModels, never()).store(any(), any(), any(), any());
    }

    @Test
    void theContainerIsAProjectionGlobalErrorWithTheSnapshot() {
      EntitySnapshotEnvelope envelope = entitySnapshotEnvelopeFor("AnImportantEntity");

      new ReadModelStore(config).project(envelope);

      assertThat(ErrorHandler.ERRORS.get(0)[1]).isEqualTo(envelope);
      assertThat(new ProjectionGlobalError(envelope, AN_ENTITY_INSTANCE, null, null, new IllegalStateException()).entityEnvelope())
          .isSameAs(envelope);
    }
  }
}
