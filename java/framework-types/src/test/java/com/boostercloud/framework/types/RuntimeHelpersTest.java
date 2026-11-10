package com.boostercloud.framework.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.boostercloud.framework.types.injectable.Injectable;
import com.boostercloud.framework.types.injectable.InjectableCommand;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Dates, environment variables, internal info, logging contract, user app registry and injectable commands. */
class RuntimeHelpersTest {

  @AfterEach
  void cleanUp() {
    Dates.resetClock();
    EnvironmentVariables.clearOverrides();
    UserAppRegistry.clear();
    System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
  }

  @Nested
  class DatesTests {
    @Test
    void formatsLikeJavaScriptToIsoString() {
      assertThat(Dates.toIso(Instant.EPOCH)).isEqualTo("1970-01-01T00:00:00.000Z");
      assertThat(Dates.toIso(Instant.parse("2024-01-31T10:20:30.999999Z"))).isEqualTo("2024-01-31T10:20:30.999Z");
      assertThat(Dates.toIso(1706696430123L)).isEqualTo("2024-01-31T10:20:30.123Z");
    }

    @Test
    void usesTheConfiguredClock() {
      Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T09:15:27.123Z"), ZoneOffset.UTC));
      assertThat(Dates.nowIso()).isEqualTo("2026-11-10T09:15:27.123Z");
      assertThat(Dates.nowEpochMillis()).isEqualTo(Instant.parse("2026-11-10T09:15:27.123Z").toEpochMilli());
      assertThat(Dates.nowEpochSeconds()).isEqualTo(Instant.parse("2026-11-10T09:15:27Z").getEpochSecond());
    }

    @Test
    void parsesLeniently() {
      assertThat(Dates.parse("2024-01-31T10:20:30Z")).isEqualTo(Instant.parse("2024-01-31T10:20:30Z"));
      assertThat(Dates.parse("2024-01-31T11:20:30+01:00")).isEqualTo(Instant.parse("2024-01-31T10:20:30Z"));
      assertThat(Dates.parse("2024-01-31T10:20:30")).isEqualTo(Instant.parse("2024-01-31T10:20:30Z"));
      assertThat(Dates.parse("2024-01-31")).isEqualTo(Instant.parse("2024-01-31T00:00:00Z"));
      assertThat(Dates.normalizeIso("2024-01-31T10:20:30.5Z")).isEqualTo("2024-01-31T10:20:30.500Z");
      assertThat(Dates.toEpochMillis("1970-01-01T00:00:01.000Z")).isEqualTo(1000);
      assertThatThrownBy(() -> Dates.parse("yesterday")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsLexicalOrderEqualToChronologicalOrder() {
      List<String> isoDates = new ArrayList<>(List.of(
          Dates.toIso(Instant.parse("2024-01-01T00:00:00Z")),
          Dates.toIso(Instant.parse("2024-01-01T00:00:00.100Z")),
          Dates.toIso(Instant.parse("2024-01-01T00:00:00.010Z"))));
      List<String> sorted = new ArrayList<>(isoDates);
      sorted.sort(null);
      assertThat(sorted).containsExactly(isoDates.get(0), isoDates.get(2), isoDates.get(1));
    }
  }

  @Nested
  class EnvironmentTests {
    @Test
    void readsOverridesBeforeTheProcessEnvironment() {
      assertThat(EnvironmentVariables.get("BOOSTER_TEST_VARIABLE")).isEmpty();
      EnvironmentVariables.set("BOOSTER_TEST_VARIABLE", "value");
      assertThat(EnvironmentVariables.get("BOOSTER_TEST_VARIABLE")).contains("value");
      EnvironmentVariables.unset("BOOSTER_TEST_VARIABLE");
      assertThat(EnvironmentVariables.get("BOOSTER_TEST_VARIABLE")).isEmpty();
    }

    @Test
    void treatsEmptyValuesAsMissingForDefaults() {
      EnvironmentVariables.set("BOOSTER_TEST_EMPTY", "");
      assertThat(EnvironmentVariables.getOrDefault("BOOSTER_TEST_EMPTY", "fallback")).isEqualTo("fallback");
    }

    @Test
    void readsTheBoosterEnvironmentFromTheSystemPropertyFirst() {
      EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, " production ");
      assertThat(EnvironmentVariables.boosterEnvironment()).contains("production");
      System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, "test");
      assertThat(EnvironmentVariables.boosterEnvironment()).contains("test");
    }

    @Test
    void defaultsTheLocalPortTo3000() {
      assertThat(InternalInfo.BOOSTER_LOCAL_PORT).isEqualTo("BOOSTER_INTERNAL_LOCAL_PORT");
      EnvironmentVariables.set(InternalInfo.BOOSTER_LOCAL_PORT, "");
      assertThat(InternalInfo.boosterLocalPort()).isEqualTo("3000");
      InternalInfo.setBoosterLocalPort(8080);
      assertThat(InternalInfo.boosterLocalPort()).isEqualTo("8080");
    }

    @Test
    void exposesTheTypeScriptConstants() {
      assertThat(SuperKind.BOOSTER_SUPER_KIND).isEqualTo("booster");
      assertThat(SuperKind.DOMAIN_SUPER_KIND).isEqualTo("domain");
      assertThat(com.boostercloud.framework.types.rockets.Rockets.ROCKET_FUNCTION_ID_ENV_VAR).isEqualTo("BOOSTER_ROCKET_FUNCTION_ID");
    }
  }

  @Nested
  class Logging {
    @Test
    void ordersLevelsLikeTypeScript() {
      assertThat(Level.DEBUG.value()).isZero();
      assertThat(Level.ERROR.value()).isEqualTo(3);
      assertThat(Level.INFO.enables(Level.WARN)).isTrue();
      assertThat(Level.WARN.enables(Level.INFO)).isFalse();
      assertThat(Level.fromValue(1)).isEqualTo(Level.INFO);
      assertThatThrownBy(() -> Level.fromValue(4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatsConsoleLinesLikeConsoleLog() {
      assertThat(ConsoleLogger.format("[Booster]: ", "a")).isEqualTo("[Booster]:  a");
      assertThat(ConsoleLogger.format("x", 2.0, Map.of("k", 1), null, true)).isEqualTo("x 2 {\"k\":1} null true");
      assertThat(ConsoleLogger.format("e", new IllegalStateException("boom"))).startsWith("e java.lang.IllegalStateException: boom");
    }

    @Test
    void writesDebugAndInfoToStdoutAndWarnAndErrorToStderr() {
      PrintStream out = System.out;
      PrintStream err = System.err;
      ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
      ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
      System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
      System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
      try {
        ConsoleLogger.INSTANCE.debug("d");
        ConsoleLogger.INSTANCE.info("i");
        ConsoleLogger.INSTANCE.warn("w");
        ConsoleLogger.INSTANCE.log(Level.ERROR, "e");
      } finally {
        System.setOut(out);
        System.setErr(err);
      }
      assertThat(capturedOut.toString(StandardCharsets.UTF_8).lines()).containsExactly("d", "i");
      assertThat(capturedErr.toString(StandardCharsets.UTF_8).lines()).containsExactly("w", "e");
    }
  }

  @Nested
  class Registries {
    @Test
    void registersTheUserApp() {
      assertThat(UserAppRegistry.find()).isEmpty();
      assertThatThrownBy(UserAppRegistry::get).hasMessageContaining("Booster.start");
      UserApp userApp = mock(UserApp.class);
      UserAppRegistry.register(userApp);
      assertThat(UserAppRegistry.get()).isSameAs(userApp);
    }

    @Test
    void buildsInjectableCommands() {
      List<String> received = new ArrayList<>();
      InjectableCommand test = InjectableCommand.of("test", "Says hello", (config, arguments) -> received.addAll(arguments));
      Injectable injectable = Injectable.of(test);
      assertThat(injectable.command("test")).containsSame(test);
      assertThat(injectable.command("other")).isEmpty();
      assertThat(test.description()).isEqualTo("Says hello");
      test.run(new BoosterConfig("test"), List.of("a", "b"));
      assertThat(received).containsExactly("a", "b");
      assertThatThrownBy(() -> new Injectable(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describesSchedules() {
      ScheduleInterface schedule = new ScheduleInterface("0/5", null, null, null, "MON-FRI", null);
      assertThat(schedule.toFiveFieldCron()).isEqualTo("0/5 * * * MON-FRI");
      assertThat(schedule.isEmpty()).isFalse();
      assertThat(new ScheduleInterface(null, null, null, null, null, null).isEmpty()).isTrue();
      assertThat(BoosterJson.toJson(schedule)).isEqualTo("{\"minute\":\"0/5\",\"weekDay\":\"MON-FRI\"}");
    }
  }
}
