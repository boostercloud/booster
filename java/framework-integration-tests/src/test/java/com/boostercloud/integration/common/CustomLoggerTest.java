package com.boostercloud.integration.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The line format of {@link CustomLogger} ({@code src/common/custom-logger.ts}). */
class CustomLoggerTest {

  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final CustomLogger logger = new CustomLogger(new PrintStream(output, true, StandardCharsets.UTF_8));

  @BeforeEach
  void pinTheClock() {
    // one instance per class in this module (junit-platform.properties): start every test with an empty output
    output.reset();
    Dates.useClock(Clock.fixed(Instant.parse("2026-11-10T10:00:00.123Z"), ZoneOffset.UTC));
  }

  @AfterEach
  void reset() {
    Dates.resetClock();
    EnvironmentVariables.clearOverrides();
  }

  private String written() {
    return output.toString(StandardCharsets.UTF_8);
  }

  @Test
  void outsideTheLocalEnvironmentLinesHaveTheLevelAndTheMessageOnly() {
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, "azure");

    logger.info("hello");
    logger.error("failed", "badly");

    assertThat(written()).isEqualTo("[INFO]: hello []\n[ERROR]: failed [\"badly\"]\n");
  }

  @Test
  void inTheLocalEnvironmentLinesAreColouredAndTimestamped() {
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, "local");

    logger.debug("d");
    logger.info("i");
    logger.warn("w");
    logger.error("e");

    assertThat(written().split("\n")).containsExactly(
        "\u001B[32m2026-11-10T10:00:00.123Z - \u001B[32m[DEBUG]: d []\u001B[0m",
        "\u001B[34m2026-11-10T10:00:00.123Z - \u001B[34m[INFO]: i []\u001B[0m",
        "\u001B[33m2026-11-10T10:00:00.123Z - \u001B[33m[WARN]: w []\u001B[0m",
        "\u001B[31m2026-11-10T10:00:00.123Z - \u001B[31m[ERROR]: e []\u001B[0m");
  }

  @Test
  void newLinesLiteralBackslashNsAndWhitespaceRunsBecomeOneSpace() {
    assertThat(CustomLogger.formatMessage("a\nb\\nc   d\t\te")).isEqualTo("a b c d e []");
  }

  @Test
  void theOptionalParametersArePrintedAsOneArrayLikeTheTypeScriptQuirk() {
    // TypeScript passes optionalParams to util.format as a single array argument: `msg [ 'a', { x: 1 } ]`; Java renders
    // the array as JSON
    assertThat(CustomLogger.formatMessage("[Booster]|Location: ", "a", Map.of("x", 1)))
        .isEqualTo("[Booster]|Location: [\"a\",{\"x\":1}]");
    assertThat(CustomLogger.formatMessage("msg", List.of("multi\nline"))).isEqualTo("msg [[\"multi line\"]]");
  }
}
