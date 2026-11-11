package com.boostercloud.framework.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-common-helpers/test/logger.test.ts}. */
class LoggerTest {

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream out;
  private ByteArrayOutputStream err;

  @BeforeEach
  void captureConsole() {
    originalOut = System.out;
    originalErr = System.err;
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreConsole() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private List<String> stdout() {
    return out.toString(StandardCharsets.UTF_8).lines().toList();
  }

  private List<String> stderr() {
    return err.toString(StandardCharsets.UTF_8).lines().toList();
  }

  private void logEverything(Logger logger) {
    logger.debug("a");
    logger.info("b");
    logger.warn("c");
    logger.error("d");
  }

  @Nested
  class WithTheConsole {
    @Test
    void inDebugLevelProvidesDebugInfoWarnAndErrorFunctions() {
      BoosterConfig config = new BoosterConfig("test");
      config.logLevel = Level.DEBUG;
      logEverything(Loggers.getLogger(config));
      assertThat(stdout()).containsExactly("[Booster]:  a", "[Booster]:  b");
      assertThat(stderr()).containsExactly("[Booster]:  c", "[Booster]:  d");
    }

    @Test
    void inInfoLevelProvidesOnlyInfoWarnAndErrorFunctions() {
      BoosterConfig config = new BoosterConfig("test");
      config.logLevel = Level.INFO;
      logEverything(Loggers.getLogger(config));
      assertThat(stdout()).containsExactly("[Booster]:  b");
      assertThat(stderr()).containsExactly("[Booster]:  c", "[Booster]:  d");
    }

    @Test
    void inWarnLevelProvidesOnlyWarnAndErrorFunctions() {
      BoosterConfig config = new BoosterConfig("test");
      config.logLevel = Level.WARN;
      logEverything(Loggers.getLogger(config));
      assertThat(stdout()).isEmpty();
      assertThat(stderr()).containsExactly("[Booster]:  c", "[Booster]:  d");
    }

    @Test
    void inErrorLevelProvidesOnlyErrorFunctions() {
      BoosterConfig config = new BoosterConfig("test");
      config.logLevel = Level.ERROR;
      logEverything(Loggers.getLogger(config));
      assertThat(stdout()).isEmpty();
      assertThat(stderr()).containsExactly("[Booster]:  d");
    }
  }

  @Nested
  class WhenALoggerInstanceIsDefinedAtConfigLevel {
    private Logger customLogger;
    private BoosterConfig config;

    @BeforeEach
    void setUp() {
      customLogger = mock(Logger.class);
      config = new BoosterConfig("test");
      config.logger = customLogger;
    }

    private void verifyAllWith(String prefix) {
      verify(customLogger).debug(prefix, "a");
      verify(customLogger).info(prefix, "b");
      verify(customLogger).warn(prefix, "c");
      verify(customLogger).error(prefix, "d");
    }

    @Test
    void usesTheConfiguredLoggerWithTheDefaultPrefix() {
      logEverything(Loggers.getLogger(config));
      verifyAllWith("[Booster]: ");
      assertThat(stdout()).isEmpty();
      assertThat(stderr()).isEmpty();
    }

    @Test
    void addsTheLocationToThePrefix() {
      logEverything(Loggers.getLogger(config, "test-location"));
      verifyAllWith("[Booster]|test-location: ");
    }

    @Test
    void usesTheConfiguredLogPrefix() {
      config.logPrefix = "MyLogger";
      logEverything(Loggers.getLogger(config));
      verifyAllWith("[MyLogger]: ");
    }

    @Test
    void usesTheLocationParameter() {
      logEverything(Loggers.getLogger(config, "ParameterLogger"));
      verifyAllWith("[Booster]|ParameterLogger: ");
    }

    @Test
    void theOverriddenPrefixWinsOverTheConfiguredOne() {
      config.logPrefix = "ConfigLogger";
      logEverything(Loggers.getLogger(config, null, "ParameterLogger"));
      verifyAllWith("[ParameterLogger]: ");
    }

    @Test
    void combinesTheOverriddenPrefixAndTheLocation() {
      config.logPrefix = "ConfigLogger";
      logEverything(Loggers.getLogger(config, "ParameterLocation", "ParameterLogger"));
      verifyAllWith("[ParameterLogger]|ParameterLocation: ");
    }

    @Test
    void passesExtraArgumentsUnformatted() {
      IllegalStateException error = new IllegalStateException("x");
      Loggers.getLogger(config, "Loc").error("message", error, 42);
      verify(customLogger).error("[Booster]|Loc: ", "message", error, 42);
    }

    @Test
    void keepsAnEmptyOverriddenPrefixAndIgnoresAnEmptyLocation() {
      Loggers.getLogger(config, "", "").info("x");
      verify(customLogger).info("[]: ", "x");
      assertThat(Loggers.prefixFor(config, "A#b")).isEqualTo("[Booster]|A#b: ");
    }

    @Test
    void takesALevelSnapshotWhenTheLoggerIsCreated() {
      config.logLevel = Level.ERROR;
      Logger logger = Loggers.getLogger(config);
      config.logLevel = Level.DEBUG;
      logger.debug("ignored");
      logger.info("ignored");
      logger.warn("ignored");
      verifyNoInteractions(customLogger);
    }
  }
}
