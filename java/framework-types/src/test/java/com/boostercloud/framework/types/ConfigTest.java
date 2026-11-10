package com.boostercloud.framework.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.boostercloud.framework.types.concepts.AuthMetadata;
import com.boostercloud.framework.types.concepts.EventStreamConfiguration;
import com.boostercloud.framework.types.concepts.RoleMetadata;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.rockets.RocketFunction;
import com.boostercloud.framework.types.sensor.BoosterHealthIndicatorsIds;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-types/test/config.test.ts}, plus the untested behaviour of {@code config.ts}. */
class ConfigTest {

  private static final int PROPERTY_RUNS = 200;

  /** fast-check {@code fc.string({ minLength: 1, maxLength: 10 })}: arbitrary characters. */
  private static void forAllStrings(Random random, Consumer<String> property) {
    for (int run = 0; run < PROPERTY_RUNS; run++) {
      property.accept(randomString(random));
    }
  }

  private static String randomString(Random random) {
    int length = 1 + random.nextInt(10);
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int codePoint = switch (random.nextInt(4)) {
        case 0 -> 'a' + random.nextInt(26);
        case 1 -> ' ' + random.nextInt(95);
        case 2 -> 0x00A0 + random.nextInt(0x2000);
        default -> 0x1F300 + random.nextInt(0x200);
      };
      builder.appendCodePoint(codePoint);
    }
    return builder.toString();
  }

  private static SchemaMigrationMetadata migration(int toVersion) {
    return new SchemaMigrationMetadata(Object.class, "method" + toVersion, toVersion, Object.class, Object.class);
  }

  @AfterEach
  void cleanUp() {
    EnvironmentVariables.clearOverrides();
  }

  @Nested
  class TheConfigType {
    @Nested
    class ResourceNamesTests {
      @Test
      void failsToGetIfTheAppNameIsEmpty() {
        BoosterConfig config = new BoosterConfig("test");
        config.appName = "";
        assertThatThrownBy(config::resourceNames).hasMessage("Application name cannot be empty");
      }

      @Test
      void getsTheApplicationStackNameFromTheAppName() {
        forAllStrings(new Random(1), appName -> {
          BoosterConfig config = new BoosterConfig("test");
          config.appName = appName;
          assertThat(config.resourceNames().applicationStack()).isEqualTo(appName + "-app");
        });
      }

      @Test
      void getsTheEventsStoreNameFromTheAppName() {
        forAllStrings(new Random(2), appName -> {
          BoosterConfig config = new BoosterConfig("test");
          config.appName = appName;
          assertThat(config.resourceNames().eventsStore()).isEqualTo(appName + "-app-events-store");
        });
      }

      @Test
      void getsWellFormattedReadModelNamesBasedOnTheApplicationName() {
        Random random = new Random(3);
        forAllStrings(random, appName -> {
          String readModelName = randomString(random);
          BoosterConfig config = new BoosterConfig("test");
          config.appName = appName;
          assertThat(config.resourceNames().forReadModel(readModelName)).isEqualTo(appName + "-app-" + readModelName);
        });
      }

      @Test
      void derivesEveryResourceNameFromTheDefaultAppName() {
        ResourceNames names = new BoosterConfig("test").resourceNames();
        assertThat(names.applicationStack()).isEqualTo("new-booster-app-app");
        assertThat(names.eventsStore()).isEqualTo("new-booster-app-app-events-store");
        assertThat(names.dispatchedEventsStore()).isEqualTo("new-booster-app-app-dispatched-events");
        assertThat(names.eventsDedup()).isEqualTo("new-booster-app-app-events-dedup");
        assertThat(names.subscriptionsStore()).isEqualTo("new-booster-app-app-subscriptions-store");
        assertThat(names.connectionsStore()).isEqualTo("new-booster-app-app-connections-store");
        assertThat(names.streamTopic()).isEqualTo("booster_events");
        assertThat(names.forReadModel("CartReadModel")).isEqualTo("new-booster-app-app-CartReadModel");
      }

      @Test
      void takesTheStreamTopicFromTheEventStreamConfiguration() {
        BoosterConfig config = new BoosterConfig("test");
        config.eventStreamConfiguration = EventStreamConfiguration.enabled(
            EventStreamConfiguration.Parameters.of("my-topic", 3, 1));
        assertThat(config.resourceNames().streamTopic()).isEqualTo("my-topic");
      }

      @Test
      void stripsTheApplicationPrefixFromResourceNames() {
        BoosterConfig config = new BoosterConfig("test");
        config.appName = "my.app";
        assertThat(config.readModelNameFromResourceName("my.app-app-CartReadModel")).isEqualTo("CartReadModel");
        assertThat(config.readModelNameFromResourceName("other-CartReadModel")).isEqualTo("other-CartReadModel");
        // TypeScript builds an unescaped regex; the literal prefix is required in Java
        assertThat(config.readModelNameFromResourceName("myXapp-app-CartReadModel")).isEqualTo("myXapp-app-CartReadModel");
      }
    }

    @Nested
    class ThereAreRoles {
      @Test
      void returnsTrueWhenThereAreRolesDefined() {
        BoosterConfig config = new BoosterConfig("test");
        config.roles.put("test-role", new RoleMetadata(new AuthMetadata(List.of(), null)));
        assertThat(config.thereAreRoles()).isTrue();
      }

      @Test
      void returnsFalseWhenThereAreNoRolesDefined() {
        assertThat(new BoosterConfig("test").thereAreRoles()).isFalse();
      }
    }

    @Nested
    class CurrentVersionFor {
      @Test
      void returnsOneWhenTheConceptDoesNotHaveAnyMigrationDefined() {
        BoosterConfig config = new BoosterConfig("test");
        Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
        migrations.put(2, migration(2));
        config.schemaMigrations.put("concept-with-migrations", migrations);
        assertThat(config.currentVersionFor("concept-without-migration")).isEqualTo(1);
      }

      @Test
      void returnsTheVersionOfTheLatestSchemaMigration() {
        BoosterConfig config = new BoosterConfig("test");
        Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
        migrations.put(3, migration(3));
        migrations.put(2, migration(2));
        config.schemaMigrations.put("concept", migrations);
        assertThat(config.currentVersionFor("concept")).isEqualTo(3);
      }

      @Test
      void returnsOneForAnEmptyMigrationMap() {
        BoosterConfig config = new BoosterConfig("test");
        config.schemaMigrations.put("concept", new LinkedHashMap<>());
        assertThat(config.currentVersionFor("concept")).isEqualTo(1);
      }
    }

    @Nested
    class Validate {
      @Test
      void throwsWhenThereAreGapsInTheMigrationVersionsForAConcept() {
        BoosterConfig config = new BoosterConfig("test");
        Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
        migrations.put(3, migration(3));
        migrations.put(2, migration(2));
        migrations.put(5, migration(5));
        config.schemaMigrations.put("concept", migrations);
        assertThatThrownBy(config::validate)
            .hasMessageContaining("Schema Migrations for 'concept' are invalid")
            .hasMessage("Schema Migrations for 'concept' are invalid: they are missing a migration with toVersion=4. "
                + "There must be a migration for 'concept' for every version in the range [2..5]");
      }

      @Test
      void doesNotThrowWhenThereAreNoGapsInTheMigrationVersionsForAConcept() {
        BoosterConfig config = new BoosterConfig("test");
        Map<Integer, SchemaMigrationMetadata> migrations = new LinkedHashMap<>();
        migrations.put(4, migration(4));
        migrations.put(2, migration(2));
        migrations.put(3, migration(3));
        config.schemaMigrations.put("concept", migrations);
        assertThatCode(config::validate).doesNotThrowAnyException();
      }
    }

    @Nested
    class Provider {
      @Test
      void throwsWhenThereIsNoProviderSet() {
        BoosterConfig config = new BoosterConfig("test");
        assertThatThrownBy(config::provider).hasMessageContaining("set a valid provider runtime");
      }

      @Test
      @SuppressWarnings("deprecation")
      void doesNotThrowWhenThereIsAProviderSet() {
        BoosterConfig config = new BoosterConfig("test");
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
          config.setProvider(mock(ProviderLibrary.class));
        } finally {
          System.setErr(originalErr);
        }
        assertThatCode(config::provider).doesNotThrowAnyException();
        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("The usage of the 'config.provider' field is deprecated");
      }

      @Test
      void reportsAProviderPackageThatCannotBeLoaded() {
        BoosterConfig config = new BoosterConfig("test");
        config.providerPackage = "com.example.missing";
        assertThatThrownBy(config::provider)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("com.example.missing.Provider");
      }
    }
  }

  @Nested
  class Defaults {
    @Test
    void hasTheTypeScriptDefaults() {
      BoosterConfig config = new BoosterConfig("production");
      assertThat(config.environmentName).isEqualTo("production");
      assertThat(config.logLevel).isEqualTo(Level.DEBUG);
      assertThat(config.logPrefix).isNull();
      assertThat(config.logger).isNull();
      assertThat(config.appName).isEqualTo("new-booster-app");
      assertThat(config.defaultResponseHeaders).isEmpty();
      assertThat(config.subscriptions.maxConnectionDurationInSeconds).isEqualTo(604800);
      assertThat(config.subscriptions.maxDurationInSeconds).isEqualTo(172800);
      assertThat(config.enableGraphQLIntrospection).isTrue();
      assertThat(config.enableSubscriptions).isTrue();
      assertThat(config.dispatchedEventsTtl).isEqualTo(300);
      assertThat(config.eventStreamConfiguration.enabled()).isFalse();
      assertThat(config.traceConfiguration.isTraceEnabledFor("ANY")).isFalse();
      assertThat(config.tokenVerifiers).isEmpty();
      assertThat(config.azureConfiguration.enableEventBatching).isTrue();
      assertThat(config.azureConfiguration.cosmos.batchSize).isEqualTo(100);
      assertThat(config.azureConfiguration.cosmos.requestOptions).isNull();
      assertThat(config.eventStoreRetry.maxRetries).isEqualTo(3);
      assertThat(config.eventStoreRetry.initialDelay).isEqualTo(1000);
      assertThat(config.eventStoreRetry.maxDelay).isEqualTo(30000);
      assertThat(config.eventStoreRetry.backoffFactor).isEqualTo(2);
      assertThat(config.eventStoreRetry.jitterFactor).isEqualTo(0.1);
      assertThat(config.eventStoreRetry.retryAllErrors).isNull();
      assertThat(config.eventStoreRetry.nonRetryableErrors).isNull();
      assertThat(config.sensorConfiguration.health.globalAuthorizer.authorize())
          .isInstanceOf(com.boostercloud.framework.types.concepts.Authorize.All.class);
      assertThat(config.sensorConfiguration.health.booster).containsOnlyKeys(
          "booster", "booster/function", "booster/database", "booster/database/events",
          "booster/database/readmodels", "rockets");
    }

    @Test
    void keepsTheTypeScriptHandlerIdentifiers() {
      BoosterConfig config = new BoosterConfig("test");
      assertThat(config.codeRelativePath).isEqualTo("dist");
      assertThat(config.eventDispatcherHandler).isEqualTo("dist/index.boosterEventDispatcher");
      assertThat(config.eventStreamConsumer).isEqualTo("dist/index.boosterConsumeEventStream");
      assertThat(config.eventStreamProducer).isEqualTo("dist/index.boosterProduceEventStream");
      assertThat(config.serveGraphQLHandler).isEqualTo("dist/index.boosterServeGraphQL");
      assertThat(config.sensorHealthHandler).isEqualTo("dist/index.boosterHealth");
      assertThat(config.scheduledTaskHandler).isEqualTo("dist/index.boosterTriggerScheduledCommand");
      assertThat(config.notifySubscribersHandler).isEqualTo("dist/index.boosterNotifySubscribers");
      assertThat(config.rocketDispatcherHandler).isEqualTo("dist/index.boosterRocketDispatcher");
      assertThat(config.functionRelativePath).isEqualTo("../dist/index.js");
    }

    @Test
    void givesEachConfigurationItsOwnHealthDefaults() {
      BoosterConfig first = new BoosterConfig("a");
      BoosterConfig second = new BoosterConfig("b");
      first.sensorConfiguration.health.booster.values().forEach(indicator -> indicator.enabled = true);
      assertThat(second.sensorConfiguration.health.get(BoosterHealthIndicatorsIds.ROOT).enabled).isFalse();
      assertThat(first.sensorConfiguration.health.get(BoosterHealthIndicatorsIds.DATABASE).enabled).isTrue();
    }
  }

  @Nested
  class UserProjectRootPath {
    @Test
    void throwsWhenNotSet() {
      BoosterConfig config = new BoosterConfig("test");
      assertThatThrownBy(config::userProjectRootPath)
          .hasMessage("Property \"userProjectRootPath\" is not set. Ensure you have called \"Booster.start\"");
      config.setUserProjectRootPath("");
      assertThatThrownBy(config::userProjectRootPath).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void returnsTheValueSet() {
      BoosterConfig config = new BoosterConfig("test");
      config.setUserProjectRootPath("/app/");
      assertThat(config.userProjectRootPath()).isEqualTo("/app/");
    }
  }

  @Nested
  class MustGetEnvironmentVar {
    @Test
    void throwsForAnUnsetVariable() {
      BoosterConfig config = new BoosterConfig("test");
      assertThatThrownBy(() -> config.mustGetEnvironmentVar("BOOSTER_TEST_SURELY_UNSET_VARIABLE"))
          .hasMessage("Missing environment variable 'BOOSTER_TEST_SURELY_UNSET_VARIABLE'");
    }

    @Test
    void returnsEmptyValues() {
      EnvironmentVariables.set("BOOSTER_TEST_EMPTY", "");
      assertThat(new BoosterConfig("test").mustGetEnvironmentVar("BOOSTER_TEST_EMPTY")).isEmpty();
    }
  }

  @Nested
  class RocketFunctions {
    static final class NamedFunction implements RocketFunction {
      @Override
      public Object apply(BoosterConfig config, Object request) {
        return "named";
      }
    }

    @Test
    void registersAndFindsRocketFunctions() {
      BoosterConfig config = new BoosterConfig("test");
      RocketFunction function = (cfg, request) -> "result";
      config.registerRocketFunction("id", function);
      assertThat(config.getRegisteredRocketFunction("id")).containsSame(function);
      assertThat(config.getRegisteredRocketFunction("constructor")).isEmpty();
      assertThat(config.registeredRocketFunctions()).containsOnlyKeys("id");
    }

    @Test
    void refusesDuplicatedIdsNamingThePreviousFunction() {
      BoosterConfig config = new BoosterConfig("test");
      config.registerRocketFunction("named", new NamedFunction());
      assertThatThrownBy(() -> config.registerRocketFunction("named", (cfg, request) -> null))
          .hasMessage("Error registering rocket function with id named: There is already a rocket function registered "
              + "under the same ID, \"NamedFunction\"");
      config.registerRocketFunction("anonymous", (cfg, request) -> null);
      assertThatThrownBy(() -> config.registerRocketFunction("anonymous", (cfg, request) -> null))
          .hasMessageEndingWith("under the same ID, \"\"");
    }
  }
}
