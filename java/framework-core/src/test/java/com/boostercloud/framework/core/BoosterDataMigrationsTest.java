package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.coreconcepts.datamigration.entities.BoosterDataMigrationEntity;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.DataMigrationParameters;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.DataMigrationMetadata;
import com.boostercloud.framework.types.concepts.DataMigrationStatus;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

/** {@code booster-data-migrations.ts} (no TypeScript test of its own; spec 09 §3.5). */
class BoosterDataMigrationsTest extends BoosterTestSupport {

  static final List<String> RUNS = Collections.synchronizedList(new ArrayList<>());
  static final List<UUID> REQUEST_IDS = Collections.synchronizedList(new ArrayList<>());

  /** Finishes itself. */
  public static class FirstMigration {
    public static void start(Register register) {
      RUNS.add("FirstMigration");
      REQUEST_IDS.add(register.requestID());
      register.events(new BoosterDataMigrationFinished("FirstMigration"));
    }
  }

  /** Does not register anything. */
  public static class SecondMigration {
    public static void start(Register register) {
      RUNS.add("SecondMigration");
    }
  }

  /** Same order as the second one. */
  public static class ThirdMigration {
    public static void start(Register register) {
      RUNS.add("ThirdMigration");
    }
  }

  /** Fails. */
  public static class FailingMigration {
    public static void start(Register register) {
      RUNS.add("FailingMigration");
      throw new IllegalStateException("migration failure");
    }
  }

  public record MigratedCart(UUID id, int quantity) {}

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private final Map<String, Object> statuses = new HashMap<>();

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    RUNS.clear();
    REQUEST_IDS.clear();
    Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:30.123Z"), ZoneOffset.UTC));
    config = Booster.config();
    config.logger = mock(Logger.class);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    Booster.configureBoosterConcepts();
  }

  private void register(Class<?> migrationClass, int order) {
    config.dataMigrationHandlers.put(migrationClass.getSimpleName(),
        new DataMigrationMetadata(migrationClass, new DataMigrationParameters(order)));
  }

  private MockedConstruction<EventStore> eventStore() {
    return mockConstruction(EventStore.class, (mock, context) -> when(mock.fetchEntitySnapshot(any(), any()))
        .thenAnswer(invocation -> {
          assertThat((String) invocation.getArgument(0)).isEqualTo("BoosterDataMigrationEntity");
          Object value = statuses.get(((UUID) invocation.getArgument(1)).value());
          if (value == null) {
            return Optional.empty();
          }
          return Optional.of(EntitySnapshotEnvelope.builder().entityTypeName("BoosterDataMigrationEntity")
              .entityID(invocation.getArgument(1)).value(value).build());
        }));
  }

  @SuppressWarnings("unchecked")
  private List<NonPersistedEventEnvelope> storedEvents() {
    ArgumentCaptor<List<NonPersistedEventEnvelope>> stored = ArgumentCaptor.forClass(List.class);
    verify(events, org.mockito.Mockito.atLeast(0)).store(stored.capture(), eq(config));
    List<NonPersistedEventEnvelope> all = new ArrayList<>();
    stored.getAllValues().forEach(all::addAll);
    return all;
  }

  @Test
  void returnsFalseWithoutMigrations() {
    try (MockedConstruction<EventStore> eventStores = eventStore()) {
      assertThat(BoosterDataMigrations.run()).isFalse();
      assertThat(eventStores.constructed()).isEmpty();
    }
    verify(events, never()).store(any(), any());
  }

  @Test
  void runsTheMigrationsThatNeverStartedInOrderAndStoresTheStartedEventFirst() {
    register(SecondMigration.class, 2);
    register(FirstMigration.class, 1);

    try (MockedConstruction<EventStore> ignored = eventStore()) {
      assertThat(BoosterDataMigrations.run()).isTrue();
    }

    assertThat(RUNS).containsExactly("FirstMigration", "SecondMigration");
    List<NonPersistedEventEnvelope> stored = storedEvents();
    assertThat(stored).extracting(NonPersistedEventEnvelope::typeName).containsExactly("BoosterDataMigrationStarted",
        "BoosterDataMigrationFinished", "BoosterDataMigrationStarted");
    NonPersistedEventEnvelope started = stored.get(0);
    assertThat(started.entityID()).isEqualTo(UUID.of("FirstMigration"));
    assertThat(started.entityTypeName()).isEqualTo("BoosterDataMigrationEntity");
    assertThat(started.superKind()).isEqualTo(SuperKindType.BOOSTER);
    assertThat(started.version()).isEqualTo(1);
    assertThat(started.currentUser()).isNull();
    assertThat(started.value()).isEqualTo(new BoosterDataMigrationStarted("FirstMigration", "2026-11-10T09:15:30.123Z"));
    NonPersistedEventEnvelope finished = stored.get(1);
    assertThat(finished.entityID()).isEqualTo(UUID.of("FirstMigration"));
    // The started and finished events have different request ids
    assertThat(finished.requestID()).isEqualTo(REQUEST_IDS.get(0)).isNotEqualTo(started.requestID());
    assertThat(stored.get(2).entityID()).isEqualTo(UUID.of("SecondMigration"));
  }

  @Test
  void equalOrdersKeepTheRegistrationOrder() {
    register(ThirdMigration.class, 2);
    register(SecondMigration.class, 2);
    register(FirstMigration.class, -1);

    try (MockedConstruction<EventStore> ignored = eventStore()) {
      BoosterDataMigrations.run();
    }

    assertThat(RUNS).containsExactly("FirstMigration", "ThirdMigration", "SecondMigration");
  }

  @Test
  void runningMigrationsAreNotStartedAgainAndDoNotBlockTheNextOnes() {
    register(FirstMigration.class, 1);
    register(SecondMigration.class, 2);
    statuses.put("FirstMigration", Map.of("id", "FirstMigration", "status", 0, "lastUpdated", "x"));

    try (MockedConstruction<EventStore> ignored = eventStore()) {
      assertThat(BoosterDataMigrations.run()).isTrue();
    }

    assertThat(RUNS).containsExactly("SecondMigration");
  }

  @Test
  void aRunningMigrationAloneStillReportsMigrating() {
    register(FirstMigration.class, 1);
    statuses.put("FirstMigration", new BoosterDataMigrationEntity("FirstMigration", DataMigrationStatus.RUNNING, "x", null));

    try (MockedConstruction<EventStore> ignored = eventStore()) {
      assertThat(BoosterDataMigrations.run()).isTrue();
    }

    assertThat(RUNS).isEmpty();
    verify(events, never()).store(any(), any());
  }

  @Test
  void finishedMigrationsAreSkipped() {
    register(FirstMigration.class, 1);
    register(SecondMigration.class, 2);
    statuses.put("FirstMigration", Map.of("id", "FirstMigration", "status", 1));
    statuses.put("SecondMigration", new BoosterDataMigrationEntity("SecondMigration", DataMigrationStatus.FINISHED, "x", null));

    try (MockedConstruction<EventStore> ignored = eventStore()) {
      assertThat(BoosterDataMigrations.run()).isFalse();
    }

    assertThat(RUNS).isEmpty();
  }

  @Test
  void aFailingMigrationStopsTheRunAfterStoringItsStartedEvent() {
    register(FailingMigration.class, 1);
    register(SecondMigration.class, 2);

    try (MockedConstruction<EventStore> ignored = eventStore()) {
      assertThatThrownBy(BoosterDataMigrations::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("migration failure");
    }

    assertThat(RUNS).containsExactly("FailingMigration");
    assertThat(storedEvents()).extracting(NonPersistedEventEnvelope::typeName).containsExactly("BoosterDataMigrationStarted");
  }

  @Test
  void migrateEntityStoresABoosterEntityMigratedEventInTheOldStream() {
    MigratedCart newEntity = new MigratedCart(UUID.of("c-9"), 200);

    BoosterDataMigrations.migrateEntity("Cart", UUID.of("c-1"), newEntity);

    List<NonPersistedEventEnvelope> stored = storedEvents();
    assertThat(stored).hasSize(1);
    NonPersistedEventEnvelope envelope = stored.get(0);
    assertThat(envelope.typeName()).isEqualTo("BoosterEntityMigrated");
    assertThat(envelope.entityTypeName()).isEqualTo("Cart");
    assertThat(envelope.entityID()).isEqualTo(UUID.of("c-1"));
    assertThat(envelope.superKind()).isEqualTo(SuperKindType.BOOSTER);
    assertThat(envelope.requestID()).isNotNull();
    assertThat(envelope.value()).isEqualTo(new BoosterEntityMigrated("Cart", UUID.of("c-1"), "MigratedCart", newEntity));
  }

  @Test
  void everyMigrateEntityCallHasItsOwnRequest() {
    BoosterDataMigrations.migrateEntity("Cart", UUID.of("c-1"), new MigratedCart(UUID.of("c-9"), 1));
    BoosterDataMigrations.migrateEntity("Cart", UUID.of("c-2"), new MigratedCart(UUID.of("c-8"), 1));

    List<NonPersistedEventEnvelope> stored = storedEvents();
    assertThat(stored).hasSize(2);
    assertThat(stored.get(0).requestID()).isNotEqualTo(stored.get(1).requestID());
  }
}
