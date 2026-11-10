package com.boostercloud.framework.core.coreconcepts.datamigration.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.BoosterDataMigrationEntityDuration;
import com.boostercloud.framework.types.concepts.DataMigrationStatus;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests of the built-in data migration entity reducers (no TypeScript unit tests exist; spec 06 §3.11.4). */
class BoosterDataMigrationEntityTest extends BoosterTestSupport {

  private static void clockAt(String instant) {
    Dates.useClock(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  @Test
  void startedIsARunningMigrationThatStartsAtReductionTime() {
    clockAt("2026-11-10T10:00:00.120Z");
    BoosterDataMigrationEntity ignored = new BoosterDataMigrationEntity("Other", DataMigrationStatus.FINISHED, "x", null);

    BoosterDataMigrationEntity entity = BoosterDataMigrationEntity.started(
        new BoosterDataMigrationStarted("CartIdDataMigrateV2", "2026-11-10T10:00:00.000Z"), ignored);

    assertThat(entity).isEqualTo(new BoosterDataMigrationEntity("CartIdDataMigrateV2", DataMigrationStatus.RUNNING,
        "2026-11-10T10:00:00.000Z", new BoosterDataMigrationEntityDuration("2026-11-10T10:00:00.120Z", null, null)));
    assertThat(BoosterJson.toRawMap(entity)).isEqualTo(Map.of("id", "CartIdDataMigrateV2", "status", 0,
        "lastUpdated", "2026-11-10T10:00:00.000Z", "duration", Map.of("start", "2026-11-10T10:00:00.120Z")));
  }

  @Test
  void finishedComputesTheDurationFromTheStartOfTheCurrentState() {
    clockAt("2026-11-10T10:05:00.480Z");
    BoosterDataMigrationEntity running = new BoosterDataMigrationEntity("CartIdDataMigrateV2", DataMigrationStatus.RUNNING,
        "2026-11-10T10:00:00.000Z", new BoosterDataMigrationEntityDuration("2026-11-10T10:00:00.120Z", null, null));

    BoosterDataMigrationEntity entity = BoosterDataMigrationEntity.finished(
        new BoosterDataMigrationFinished("CartIdDataMigrateV2", "2026-11-10T10:05:00.000Z"), running);

    assertThat(entity).isEqualTo(new BoosterDataMigrationEntity("CartIdDataMigrateV2", DataMigrationStatus.FINISHED,
        "2026-11-10T10:05:00.000Z", new BoosterDataMigrationEntityDuration("2026-11-10T10:00:00.120Z",
            "2026-11-10T10:05:00.480Z", 300360L)));
    assertThat(BoosterJson.toRawMap(entity)).containsEntry("status", 1)
        .containsEntry("duration", Map.of("start", "2026-11-10T10:00:00.120Z", "end", "2026-11-10T10:05:00.480Z",
            "elapsedMilliseconds", 300360));
  }

  @Test
  void finishedHasNoDurationWhenTheCurrentStateHasNoStart() {
    BoosterDataMigrationFinished event = new BoosterDataMigrationFinished("M", "2026-11-10T10:05:00.000Z");

    assertThat(BoosterDataMigrationEntity.finished(event,
        new BoosterDataMigrationEntity("M", DataMigrationStatus.RUNNING, "t", null)))
        .isEqualTo(new BoosterDataMigrationEntity("M", DataMigrationStatus.FINISHED, "2026-11-10T10:05:00.000Z", null));
    assertThat(BoosterDataMigrationEntity.finished(event, new BoosterDataMigrationEntity("M", DataMigrationStatus.RUNNING,
        "t", new BoosterDataMigrationEntityDuration("", null, null))).duration()).isNull();
  }

  @Test
  void finishedWritesNoElapsedTimeWhenTheStartIsNotADate() {
    BoosterDataMigrationEntity entity = BoosterDataMigrationEntity.finished(new BoosterDataMigrationFinished("M"),
        new BoosterDataMigrationEntity("M", DataMigrationStatus.RUNNING, "t",
            new BoosterDataMigrationEntityDuration("not a date", null, null)));

    assertThat(entity.duration().start()).isEqualTo("not a date");
    assertThat(entity.duration().elapsedMilliseconds()).isNull();
  }

  @Test
  void finishedWithoutPreviousStateFails() {
    assertThatThrownBy(() -> BoosterDataMigrationEntity.finished(new BoosterDataMigrationFinished("M"), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Cannot read the duration of the data migration M");
  }

  @Test
  void theEventStoreReducesTheBuiltInEventsOfADataMigration() {
    BoosterConfig config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    ProviderEventsLibrary events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    setProvider(config, provider);
    config.events.put("BoosterDataMigrationStarted", new EventMetadata(BoosterDataMigrationStarted.class));
    config.events.put("BoosterDataMigrationFinished", new EventMetadata(BoosterDataMigrationFinished.class));
    config.reducers.put("BoosterDataMigrationStarted", new ReducerMetadata(BoosterDataMigrationEntity.class, "started"));
    config.reducers.put("BoosterDataMigrationFinished", new ReducerMetadata(BoosterDataMigrationEntity.class, "finished"));
    config.entities.put("BoosterDataMigrationEntity", new EntityMetadata(BoosterDataMigrationEntity.class, null));
    UUID id = UUID.of("CartIdDataMigrateV2");
    EventEnvelope started = builtIn("BoosterDataMigrationStarted", "2026-11-10T10:00:00.000Z");
    EventEnvelope finished = builtIn("BoosterDataMigrationFinished", "2026-11-10T10:05:00.000Z");
    when(events.forEntitySince(config, "BoosterDataMigrationEntity", id, EventStore.ORIGIN_OF_TIME))
        .thenReturn(List.of(started, finished));
    when(events.storeSnapshot(any(), eq(config))).thenAnswer(invocation ->
        invocation.<NonPersistedEntitySnapshotEnvelope>getArgument(0).persisted("2026-11-10T10:05:01.000Z"));
    clockAt("2026-11-10T10:05:00.480Z");

    Optional<EntitySnapshotEnvelope> snapshot = new EventStore(config).fetchEntitySnapshot("BoosterDataMigrationEntity", id);

    assertThat(snapshot).get().satisfies(envelope -> {
      assertThat(envelope.superKind()).isEqualTo(SuperKindType.BOOSTER);
      assertThat(envelope.snapshottedEventCreatedAt()).isEqualTo("2026-11-10T10:05:00.000Z");
      // both reductions ran at the same (fixed) wall-clock time
      assertThat(envelope.value()).isEqualTo(new BoosterDataMigrationEntity("CartIdDataMigrateV2",
          DataMigrationStatus.FINISHED, "2026-11-10T10:05:00.000Z", new BoosterDataMigrationEntityDuration(
              "2026-11-10T10:05:00.480Z", "2026-11-10T10:05:00.480Z", 0L)));
    });
  }

  @SuppressWarnings("deprecation")
  private static void setProvider(BoosterConfig config, ProviderLibrary provider) {
    config.setProvider(provider);
  }

  private static EventEnvelope builtIn(String typeName, String lastUpdated) {
    return EventEnvelope.builder()
        .version(1)
        .superKind(SuperKindType.BOOSTER)
        .entityID(UUID.of("CartIdDataMigrateV2"))
        .entityTypeName("BoosterDataMigrationEntity")
        .typeName(typeName)
        .value(Map.of("name", "CartIdDataMigrateV2", "lastUpdated", lastUpdated))
        .requestID(UUID.generate())
        .createdAt(lastUpdated)
        .build();
  }
}
