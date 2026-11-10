package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.coreconcepts.datamigration.entities.BoosterDataMigrationEntity;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.core.fixtures.app.App;
import com.boostercloud.framework.core.services.EventStore;
import com.boostercloud.framework.core.services.tokenverifiers.JwksUriTokenVerifier;
import com.boostercloud.framework.core.services.tokenverifiers.JwtEnvVars;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.PaginatedEntitiesIdsResult;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Port of {@code test/booster.test.ts}, plus the untested parts of {@code booster.ts}. */
class BoosterTest extends BoosterTestSupport {

  public record TestReadModel(String id) {
    public String getId() {
      return id;
    }
  }

  public record SomeEntity(String id) {
    public String getId() {
      return id;
    }
  }

  @Test
  void configuresTheCurrentEnvironmentAndRecordsEveryEnvironment() {
    Booster.configure("test", config -> {
      config.appName = "test-app-name";
      config.providerPackage = "some-provider";
    });
    Booster.configure("another-environment", config -> config.appName = "this-shouldnt-be-set");

    assertThat(Booster.configuredEnvironments()).containsExactly("test", "another-environment");
    assertThat(Booster.config().appName).isEqualTo("test-app-name");
    assertThat(Booster.config().providerPackage).isEqualTo("some-provider");
    assertThat(Booster.config().environmentName).isEqualTo("test");
  }

  @Test
  void configureCurrentEnvRunsUnconditionallyWithoutRecordingEnvironments() {
    Booster.configureCurrentEnv(config -> config.appName = "x");

    assertThat(Booster.config().appName).isEqualTo("x");
    assertThat(Booster.configuredEnvironments()).isEmpty();
  }

  @Test
  void theEnvironmentIsRequired() {
    String property = System.getProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv(EnvironmentVariables.BOOSTER_ENV) == null);
    System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    try {
      Booster.reset();
      assertThatThrownBy(Booster::config).isInstanceOf(IllegalStateException.class)
          .hasMessage("Booster environment is missing. You need to provide an environment to configure your Booster project");
      assertThat(Booster.findConfig()).isEmpty();
      EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, "production");
      assertThat(Booster.config().environmentName).isEqualTo("production");
    } finally {
      System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, property);
    }
  }

  @Test
  void startImportsTheApplicationRegistersTheConceptsAndTheUserApp() {
    Booster.start(App.class);

    BoosterConfig config = Booster.config();
    assertThat(config.commandHandlers).containsKey("CreateCart");
    assertThat(config.userProjectRootPath()).endsWith("framework-core" + java.io.File.separator);
    assertThat(Path.of(config.userProjectRootPath()).resolve("pom.xml")).exists();
    assertThat(UserAppRegistry.get()).isInstanceOf(BoosterUserApp.class);
    assertThat(config.events).containsKeys("BoosterEntityMigrated", "BoosterDataMigrationStarted",
        "BoosterDataMigrationFinished", "BoosterEntityTouched");
  }

  @Test
  void startValidatesTheSchemaMigrations() {
    Booster.configureCurrentEnv(config -> config.schemaMigrations.put("Gap", new java.util.LinkedHashMap<>(Map.of(3,
        new com.boostercloud.framework.types.concepts.SchemaMigrationMetadata(Object.class, "m", 3, Object.class, Object.class)))));

    assertThatThrownBy(() -> Booster.start(App.class)).isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Schema Migrations for 'Gap' are invalid: they are missing a migration with toVersion=2.");
  }

  @Test
  void registersTheBuiltInConceptsOverwritingUserEntries() {
    BoosterConfig config = Booster.config();
    config.events.put("BoosterEntityTouched", new EventMetadata(String.class));

    Booster.configureBoosterConcepts();

    assertThat(config.events).containsExactly(
        Map.entry("BoosterEntityTouched", new EventMetadata(BoosterEntityTouched.class)),
        Map.entry("BoosterEntityMigrated", new EventMetadata(BoosterEntityMigrated.class)),
        Map.entry("BoosterDataMigrationStarted", new EventMetadata(BoosterDataMigrationStarted.class)),
        Map.entry("BoosterDataMigrationFinished", new EventMetadata(BoosterDataMigrationFinished.class)));
    assertThat(config.reducers).containsExactly(
        Map.entry("BoosterDataMigrationStarted", new ReducerMetadata(BoosterDataMigrationEntity.class, "started")),
        Map.entry("BoosterDataMigrationFinished", new ReducerMetadata(BoosterDataMigrationEntity.class, "finished")));
    assertThat(config.entities).containsExactly(Map.entry("BoosterDataMigrationEntity",
        new EntityMetadata(BoosterDataMigrationEntity.class, BoosterAuthorizer.DENY_ACCESS)));
  }

  @Test
  void readModelReturnsASearcherBackedByTheReadModelsReader() {
    try (MockedConstruction<BoosterReadModelsReader> readers = mockConstruction(BoosterReadModelsReader.class,
        (reader, context) -> when(reader.readModelSearch(eq(TestReadModel.class), any(), any(), any(), any(),
            org.mockito.ArgumentMatchers.anyBoolean(), any())).thenReturn(List.of(new TestReadModel("42"))))) {
      List<TestReadModel> results = Booster.readModel(TestReadModel.class).search();

      assertThat(results).extracting(TestReadModel::getId).containsExactly("42");
      BoosterReadModelsReader reader = readers.constructed().getFirst();
      verify(reader, times(1)).readModelSearch(eq(TestReadModel.class), eq(Map.of()), eq(Map.of()), isNull(), isNull(),
          eq(false), isNull());
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  void entitiesIDsCallsTheProviderWithItsParameterOrder() {
    ProviderLibrary provider = mock(ProviderLibrary.class);
    ProviderEventsLibrary events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    PaginatedEntitiesIdsResult page = new PaginatedEntitiesIdsResult(List.of(), 0, null);
    when(events.searchEntitiesIDs(any(), eq(1), isNull(), eq("TestEvent"))).thenReturn(page);
    Booster.config().setProvider(provider);

    assertThat(Booster.entitiesIDs("TestEvent", 1, null)).isSameAs(page);
    verify(events, times(1)).searchEntitiesIDs(Booster.config(), 1, null, "TestEvent");
  }

  @Test
  void eventsDelegatesToTheEventSearch() {
    EventParametersFilterByType request = new EventParametersFilterByType("TestEvent", null, null, null);
    List<EventSearchResponse> responses = List.of();
    try (MockedStatic<BoosterEventSearch> search = mockStatic(BoosterEventSearch.class)) {
      search.when(() -> BoosterEventSearch.eventSearch(Booster.config(), request)).thenReturn(responses);

      assertThat(Booster.events(request)).isSameAs(responses);
      search.verify(() -> BoosterEventSearch.eventSearch(Booster.config(), request), times(1));
    }
  }

  @Test
  void deleteEventDelegatesToTheDeleteEventDispatcher() {
    EventDeleteParameters parameters = new EventDeleteParameters("Cart", UUID.of("c"), "2026-01-01T00:00:00.000Z");
    try (MockedStatic<BoosterDeleteEventDispatcher> dispatcher = mockStatic(BoosterDeleteEventDispatcher.class)) {
      dispatcher.when(() -> BoosterDeleteEventDispatcher.deleteEvent(Booster.config(), parameters)).thenReturn(true);

      assertThat(Booster.deleteEvent(parameters)).isTrue();
    }
  }

  @Test
  void entityBuildsAnInstanceFromTheSnapshot() {
    EntitySnapshotEnvelope snapshot = EntitySnapshotEnvelope.builder().value(Map.of("id", "42")).build();
    try (MockedConstruction<EventStore> stores = mockConstruction(EventStore.class,
        (store, context) -> when(store.fetchEntitySnapshot("SomeEntity", UUID.of("42"))).thenReturn(Optional.of(snapshot)))) {
      Optional<SomeEntity> entity = Booster.entity(SomeEntity.class, UUID.of("42"));

      assertThat(entity).contains(new SomeEntity("42"));
      verify(stores.constructed().getFirst(), times(1)).fetchEntitySnapshot("SomeEntity", UUID.of("42"));
    }
  }

  @Test
  void entityWithoutValueIsAnEmptyInstanceAndWithoutSnapshotIsEmpty() {
    try (MockedConstruction<EventStore> ignored = mockConstruction(EventStore.class, (store, context) -> {
      when(store.fetchEntitySnapshot("SomeEntity", UUID.of("42"))).thenReturn(Optional.of(EntitySnapshotEnvelope.builder().build()));
      when(store.fetchEntitySnapshot("SomeEntity", UUID.of("43"))).thenReturn(Optional.empty());
    })) {
      assertThat(Booster.entity(SomeEntity.class, UUID.of("42"))).get().extracting(SomeEntity::getId).isNull();
      assertThat(Booster.entity(SomeEntity.class, UUID.of("43"))).isEmpty();
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  void loadsTheTokenVerifierFromTheEnvironment() {
    EnvironmentVariables.set(JwtEnvVars.BOOSTER_JWT_ISSUER, "booster");
    EnvironmentVariables.set(JwtEnvVars.BOOSTER_JWKS_URI, "https://myauth0app.auth0.com/.well-known/jwks.json");
    EnvironmentVariables.set(JwtEnvVars.BOOSTER_ROLES_CLAIM, "booster:role");

    Booster.loadTokenVerifierFromEnv();

    assertThat(Booster.config().tokenVerifiers).singleElement().isInstanceOfSatisfying(JwksUriTokenVerifier.class, verifier -> {
      assertThat(verifier.issuer()).isEqualTo("booster");
      assertThat(verifier.jwksUri()).isEqualTo("https://myauth0app.auth0.com/.well-known/jwks.json");
      assertThat(verifier.rolesClaim()).isEqualTo("booster:role");
    });
  }

  @Test
  @SuppressWarnings("deprecation")
  void doesNotLoadTheTokenVerifierWhenAVariableIsMissing() {
    EnvironmentVariables.set(JwtEnvVars.BOOSTER_JWT_ISSUER, "booster");
    EnvironmentVariables.set(JwtEnvVars.BOOSTER_JWKS_URI, "https://example.com");

    Booster.loadTokenVerifierFromEnv();

    assertThat(Booster.config().tokenVerifiers).isEmpty();
  }

  @Test
  void theAppViewDelegatesToTheFacade() {
    Booster.app().configure("test", config -> config.appName = "from-view");

    assertThat(Booster.app().config()).isSameAs(Booster.config());
    assertThat(Booster.config().appName).isEqualTo("from-view");
    assertThat(Booster.app().configuredEnvironments()).containsExactly("test");
  }
}
