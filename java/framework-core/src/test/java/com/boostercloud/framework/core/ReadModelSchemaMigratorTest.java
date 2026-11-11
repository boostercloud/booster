package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Port of {@code test/read-model-schema-migrator.test.ts}, plus the {@code migrateAll} paging and store details. */
class ReadModelSchemaMigratorTest {

  public record TestConceptV1(String id, String field1) {}

  public record TestConceptV2(String id, String field1, double field2) {}

  public record TestConceptV3(String id, String field1, double field2, String field3) {}

  public static class TestConceptMigration {
    public TestConceptV2 addField2(TestConceptV1 old) {
      return new TestConceptV2(old.id(), old.field1(), 2);
    }

    public TestConceptV3 addField3(TestConceptV2 old) {
      return new TestConceptV3(old.id(), old.field1(), old.field2(), "default");
    }
  }

  private static Map<Integer, SchemaMigrationMetadata> migrations() {
    Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
    migrations.put(2, new SchemaMigrationMetadata(TestConceptMigration.class, "addField2", 2, TestConceptV1.class, TestConceptV2.class));
    migrations.put(3, new SchemaMigrationMetadata(TestConceptMigration.class, "addField3", 3, TestConceptV2.class, TestConceptV3.class));
    return migrations;
  }

  private static Map<String, Object> readModel(Object id, Map<String, Object> boosterMetadata, Object... keysAndValues) {
    Map<String, Object> readModel = new LinkedHashMap<>();
    readModel.put("id", id);
    if (boosterMetadata != null) {
      readModel.put("boosterMetadata", boosterMetadata);
    }
    for (int i = 0; i < keysAndValues.length; i += 2) {
      readModel.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return readModel;
  }

  private static Map<String, Object> metadata(Object... keysAndValues) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      metadata.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return metadata;
  }

  private BoosterConfig config;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
  }

  @Nested
  class Migrate {
    private ReadModelSchemaMigrator migrator;

    @BeforeEach
    void setUp() {
      config.schemaMigrations.put("TestConcept", migrations());
      migrator = new ReadModelSchemaMigrator(config);
    }

    @Test
    void throwsWhenTheSchemaVersionIsLowerThan1() {
      assertThatThrownBy(() -> migrator.migrate(readModel("id", metadata("version", 0, "schemaVersion", 0)), "TestConcept"))
          .isInstanceOf(InvalidVersionError.class)
          .hasMessage("Received an invalid schema version value, 0, for TestConcept. Versions must be greater than 0");
    }

    @Test
    void throwsWhenTheSchemaVersionIsHigherThanTheCurrentOne() {
      assertThatThrownBy(() -> migrator.migrate(readModel("id", metadata("version", 0, "schemaVersion", 4)), "TestConcept"))
          .isInstanceOf(InvalidVersionError.class)
          .hasMessageContaining("The current schema version of TestConcept is 3, which is lower than the received version 4")
          .hasMessage("Can not migrate schema an unknown version: The current schema version of TestConcept is 3, which is"
              + " lower than the received version 4");
    }

    @Test
    void doesNotMigrateWhenTheSchemaVersionIsTheCurrentOne() {
      Map<String, Object> toMigrate = readModel("id", metadata("version", 0, "schemaVersion", 3));

      assertThat(migrator.migrate(toMigrate, "TestConcept")).isSameAs(toMigrate);
    }

    @Test
    void migratesWhenTheSchemaVersionIsLowerThanTheCurrentOne() {
      Map<String, Object> toMigrate = readModel("id", metadata("version", 0, "schemaVersion", 1), "field1", "test-field1");

      Map<String, Object> got = migrator.migrate(toMigrate, "TestConcept");

      assertThat(got).isNotSameAs(toMigrate)
          .isEqualTo(readModel("id", metadata("version", 0, "schemaVersion", 3), "field1", "test-field1", "field2", 2,
              "field3", "default"));
      assertThat(toMigrate).isEqualTo(readModel("id", metadata("version", 0, "schemaVersion", 1), "field1", "test-field1"));
    }

    @Test
    void anAbsentSchemaVersionIsVersion1AndOtherMetadataIsKept() {
      Map<String, Object> toMigrate = readModel("id", metadata("version", 7, "optimisticConcurrencyValue", "etag",
          "lastUpdateAt", "2024-01-31T10:20:30.123Z"), "field1", "a");

      Map<String, Object> got = migrator.migrate(toMigrate, "TestConcept");

      assertThat(got.get("boosterMetadata")).isEqualTo(metadata("version", 7, "optimisticConcurrencyValue", "etag",
          "lastUpdateAt", "2024-01-31T10:20:30.123Z", "schemaVersion", 3));
      assertThat(got).containsEntry("field3", "default");
    }

    @Test
    void readModelsWithoutMetadataNorMigrationsAreCurrent() {
      Map<String, Object> toMigrate = readModel("id", null);

      assertThat(migrator.migrate(toMigrate, "Other")).isSameAs(toMigrate);
    }

    @Test
    void reportsGapsBetweenVersions() {
      Map<Integer, SchemaMigrationMetadata> gapped = new LinkedHashMap<>();
      gapped.put(3, migrations().get(3));
      config.schemaMigrations.put("Gapped", gapped);

      assertThatThrownBy(() -> migrator.migrate(readModel("id", metadata("schemaVersion", 1)), "Gapped"))
          .isInstanceOf(InvalidVersionError.class)
          .hasMessage("Received an undefined schema migration value. Are there \"gaps\" between the versions of the schema"
              + " migrations?");
    }
  }

  @Nested
  class MigrateAll {
    private ProviderReadModelsLibrary readModels;

    @BeforeEach
    @SuppressWarnings("deprecation")
    void setUp() {
      ProviderLibrary provider = mock(ProviderLibrary.class);
      readModels = mock(ProviderReadModelsLibrary.class);
      when(provider.readModels()).thenReturn(readModels);
      config.setProvider(provider);
    }

    @Test
    void returns0WhenThereAreNoReadModelsToMigrate() {
      when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(ReadModelListResult.of(List.of()));

      assertThat(new ReadModelSchemaMigrator(config).migrateAll("TestConcept")).isZero();
      verify(readModels, never()).store(any(), any(), any(), any());
    }

    @Test
    void migratesTheReadModelsWithAnOldSchemaVersionAndAShortPageEndsTheLoop() {
      config.schemaMigrations.put("TestConcept", migrations());
      when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new ReadModelListResult<>(
          List.of(readModel(1, null), readModel(2, null), readModel(3, null)), 3, Map.of("id", "10")));

      assertThat(new ReadModelSchemaMigrator(config).migrateAll("TestConcept")).isEqualTo(3);
      verify(readModels, times(1)).search(any(), any(), any(), any(), any(), any(), any(), any());
      verify(readModels, times(3)).store(eq(config), eq("TestConcept"), anyMap(), eq(0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchesWithTheMigrationFilterAndStoresTheNewVersions() {
      config.schemaMigrations.put("TestConcept", migrations());
      when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(new ReadModelListResult<>(
          List.of(readModel("a", metadata("version", 4, "schemaVersion", 2), "field1", "x", "field2", 2)), 1, null));

      new ReadModelSchemaMigrator(config).migrateAll("TestConcept");

      ArgumentCaptor<Map<String, Object>> filter = ArgumentCaptor.forClass(Map.class);
      verify(readModels).search(eq(config), eq("TestConcept"), filter.capture(), eq(Map.of()), eq(100), isNull(), eq(true),
          isNull());
      assertThat(filter.getValue()).isEqualTo(Map.of("or", List.of(
          Map.of("boosterMetadata", Map.of("schemaVersion", Map.of("lt", 3))),
          Map.of("boosterMetadata", Map.of("schemaVersion", Map.of("isDefined", false))))));
      verify(readModels).store(config, "TestConcept",
          readModel("a", metadata("version", 5L, "schemaVersion", 3), "field1", "x", "field2", 2, "field3", "default"), 4L);
    }

    @Test
    void followsTheCursorWhileThePagesAreFull() {
      config.schemaMigrations.put("TestConcept", migrations());
      List<Object> cursors = new ArrayList<>();
      when(readModels.search(any(), any(), any(), any(), anyInt(), any(), any(), any())).thenAnswer(invocation -> {
        Object cursor = invocation.getArgument(5);
        cursors.add(cursor);
        if (cursor == null) {
          return new ReadModelListResult<>(List.of(readModel(1, null), readModel(2, null)), 2, Map.of("id", "2"));
        }
        if (cursor.equals(Map.of("id", "2"))) {
          return new ReadModelListResult<>(List.of(readModel(3, null), readModel(4, null)), 2, Map.of("id", "4"));
        }
        return new ReadModelListResult<>(List.of(readModel(5, null)), 1, Map.of("id", "5"));
      });

      assertThat(new ReadModelSchemaMigrator(config).migrateAll("TestConcept", 2)).isEqualTo(5);
      assertThat(cursors).containsExactly(null, Map.of("id", "2"), Map.of("id", "4"));
      verify(readModels, times(5)).store(eq(config), eq("TestConcept"), anyMap(), anyLong());
    }

    @Test
    void stampsTheCurrentVersionWhenTheReadModelHasNoMigrations() {
      when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(ReadModelListResult.of(List.of(readModel("a", null, "field", 1))));

      new ReadModelSchemaMigrator(config).migrateAll("Plain");

      verify(readModels).store(config, "Plain", readModel("a", metadata("schemaVersion", 1, "version", 1L), "field", 1), 0L);
    }

    @Test
    void aStoreFailureFailsTheMigrationWithoutRetries() {
      config.schemaMigrations.put("TestConcept", migrations());
      when(readModels.search(any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(ReadModelListResult.of(List.of(readModel(1, null))));
      doThrow(new OptimisticConcurrencyUnexpectedVersionError("conflict")).when(readModels).store(any(), any(), any(), any());

      assertThatThrownBy(() -> new ReadModelSchemaMigrator(config).migrateAll("TestConcept"))
          .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
          .hasMessage("conflict");
      verify(readModels, times(1)).store(any(), any(), any(), any());
    }
  }
}
