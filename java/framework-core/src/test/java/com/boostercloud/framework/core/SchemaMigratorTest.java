package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.errors.InvalidVersionError;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/schema-migrator.test.ts}. */
class SchemaMigratorTest extends BoosterTestSupport {

  public record TestConcept(String field1, Double field2, String field3) {}

  public record TestConceptV1(String field1) {}

  public record TestConceptV2(String field1, double field2) {}

  public record TestConceptV3(String field1, double field2, String field3) {}

  @SchemaMigration(TestConcept.class)
  public static class TestConceptMigration {
    @ToVersion(toVersion = 2, fromSchema = TestConceptV1.class, toSchema = TestConceptV2.class)
    public TestConceptV2 addField2(TestConceptV1 old) {
      return new TestConceptV2(old.field1(), 2);
    }

    @ToVersion(toVersion = 3, fromSchema = TestConceptV2.class, toSchema = TestConceptV3.class)
    public TestConceptV3 addField3(TestConceptV2 old) {
      return new TestConceptV3(old.field1(), old.field2(), "default");
    }
  }

  private BoosterConfig config;
  private SchemaMigrator migrator;

  @BeforeEach
  void setUp() {
    Decorators.registerClass(TestConceptMigration.class);
    config = Booster.config();
    migrator = new SchemaMigrator(config);
  }

  private static EventEnvelope event(int version, Object value) {
    return EventEnvelope.builder().requestID(UUID.of("requestID")).typeName("TestConcept").version(version).value(value).build();
  }

  @Test
  void rejectsVersionsLowerThan1() {
    assertThatThrownBy(() -> migrator.migrate(event(0, Map.of())))
        .isInstanceOf(InvalidVersionError.class)
        .hasMessage("Received an invalid schema version value, 0, for TestConcept. Versions must be greater than 0");
  }

  @Test
  void rejectsVersionsNewerThanTheCurrentOne() {
    assertThatThrownBy(() -> migrator.migrate(event(4, Map.of())))
        .isInstanceOf(InvalidVersionError.class)
        .hasMessage("Can not migrate schema an unknown version: The current schema version of TestConcept is 3, which is"
            + " lower than the received version 4");
  }

  @Test
  void returnsTheSameEnvelopeWhenItIsCurrent() {
    EventEnvelope toMigrate = event(3, Map.of("field1", "a"));

    assertThat(migrator.migrate(toMigrate)).isSameAs(toMigrate);
  }

  @Test
  void migratesStepByStepIntoANewEnvelope() {
    EventEnvelope toMigrate = event(1, Map.of("field1", "test-field1"));

    EventEnvelope migrated = migrator.migrate(toMigrate);

    assertThat(migrated).isNotSameAs(toMigrate)
        .isEqualTo(event(3, new TestConceptV3("test-field1", 2, "default")));
    assertThat(migrated.value().getClass().getSimpleName()).isEqualTo("TestConceptV3");
  }

  @Test
  void migratesCommandValuesBackToRawInput() {
    CommandEnvelope command = CommandEnvelope.builder().requestID(UUID.of("r")).typeName("TestConcept").version(1)
        .value(Map.of("field1", "test-field1")).build();

    CommandEnvelope migrated = migrator.migrate(command);

    assertThat(migrated.version()).isEqualTo(3);
    assertThat(migrated.value()).isEqualTo(Map.of("field1", "test-field1", "field2", 2, "field3", "default"));
  }

  @Test
  void migratesSnapshots() {
    EntitySnapshotEnvelope snapshot = EntitySnapshotEnvelope.builder().typeName("TestConcept").entityTypeName("TestConcept")
        .version(2).value(Map.of("field1", "x", "field2", 2)).build();

    EntitySnapshotEnvelope migrated = migrator.migrate(snapshot);

    assertThat(migrated.version()).isEqualTo(3);
    assertThat(migrated.value()).isEqualTo(new TestConceptV3("x", 2, "default"));
  }

  @Test
  void conceptsWithoutMigrationsAreVersion1() {
    EventEnvelope envelope = EventEnvelope.builder().typeName("Other").version(1).value(Map.of()).build();

    assertThat(migrator.migrate(envelope)).isSameAs(envelope);
  }

  @Test
  void reportsGapsBetweenVersions() {
    Map<Integer, SchemaMigrationMetadata> gapped = new LinkedHashMap<>();
    gapped.put(3, config.schemaMigrations.get("TestConcept").get(3));
    config.schemaMigrations.put("Gapped", gapped);

    assertThatThrownBy(() -> migrator.migrate(EventEnvelope.builder().typeName("Gapped").version(1).value(Map.of()).build()))
        .isInstanceOf(InvalidVersionError.class)
        .hasMessage("Received an undefined schema migration value. Are there \"gaps\" between the versions of the schema migrations?");
  }
}
