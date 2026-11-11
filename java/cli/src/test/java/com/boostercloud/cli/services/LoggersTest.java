package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.common.ColorSupport;
import com.boostercloud.cli.testing.RecordingOraLogger;
import com.boostercloud.cli.testing.TestProjects;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Port of {@code test/services/logger.test.ts}, plus the spinner behaviour of ora. */
class LoggersTest {
  @TempDir Path dir;

  @Nested
  class LoggerAdapter {
    private final String message = "this is a message for the logger";
    private final RecordingOraLogger ora = new RecordingOraLogger();
    private final Logger logger = Loggers.logger(ora);

    @Test
    void logsADebugMessageAsAWarning() {
      logger.debug(message);
      assertThat(ora.texts("warn")).containsExactly(message);
    }

    @Test
    void logsAnInfoMessage() {
      logger.info(message);
      assertThat(ora.texts("info")).containsExactly(message);
    }

    @Test
    void logsAWarning() {
      logger.warn(message);
      assertThat(ora.texts("warn")).containsExactly(message);
    }

    @Test
    void logsAnErrorMessageAsAFailure() {
      logger.error(message);
      assertThat(ora.texts("fail")).containsExactly(message);
    }
  }

  @Nested
  class AppendOnErrorsFile {
    @Test
    void writesTimestampedLinesInErrorsLog() {
      Clock clock = Clock.fixed(Instant.parse("2024-01-31T10:20:30Z"), ZoneOffset.UTC);
      Loggers.appendOnErrorsFile(dir, clock, "this is a message\nseparated with new line");
      Loggers.appendOnErrorsFile(dir, clock, "\nsecond");
      assertThat(TestProjects.read(dir.resolve("errors.log")))
          .isEqualTo(
              "[2024-01-31T10:20:30.000Z] this is a message\n"
                  + "[2024-01-31T10:20:30.000Z] separated with new line"
                  + "[2024-01-31T10:20:30.000Z] \n"
                  + "[2024-01-31T10:20:30.000Z] second");
    }
  }

  @Nested
  class Spinner {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final OraLogger ora = new OraLogger(new PrintStream(bytes, true, StandardCharsets.UTF_8), false, true);

    @Test
    void printsOraLinesWhenNotATerminal() {
      ColorSupport.setLevel(0);
      ora.start("Building project");
      ora.succeed("Building project");
      ora.start("Deploying");
      ora.fail("Deploying");
      ora.info("info");
      ora.warn("careful");
      String nl = System.lineSeparator();
      assertThat(bytes.toString(StandardCharsets.UTF_8))
          .isEqualTo(
              "- Building project" + nl + "✔ Building project" + nl + "- Deploying" + nl + "✖ Deploying" + nl
                  + "ℹ info" + nl + "⚠ careful" + nl);
    }

    @Test
    void usesAsciiSymbolsWithoutUnicode() {
      ColorSupport.setLevel(0);
      OraLogger ascii = new OraLogger(new PrintStream(bytes, true, StandardCharsets.UTF_8), false, false);
      ascii.succeed("a");
      ascii.fail("b");
      ascii.info("c");
      assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("√ a").contains("× b").contains("i c");
    }

    @Test
    void animatesAndClearsTheSpinnerOnATerminal() throws InterruptedException {
      ColorSupport.setLevel(0);
      OraLogger animated = new OraLogger(new PrintStream(bytes, true, StandardCharsets.UTF_8), true, true);
      animated.start("Working");
      assertThat(animated.isSpinning()).isTrue();
      Thread.sleep(200);
      animated.succeed("Working");
      assertThat(animated.isSpinning()).isFalse();
      String output = bytes.toString(StandardCharsets.UTF_8);
      assertThat(output).contains("⠋ Working").contains("\r\u001b[2K").endsWith("✔ Working" + System.lineSeparator());
    }

    @Test
    void pausePersistsTheCurrentStep() {
      ColorSupport.setLevel(0);
      OraLogger animated = new OraLogger(new PrintStream(bytes, true, StandardCharsets.UTF_8), true, true);
      animated.start("Deploying");
      animated.pause();
      assertThat(animated.isSpinning()).isFalse();
      assertThat(bytes.toString(StandardCharsets.UTF_8)).endsWith("- Deploying" + System.lineSeparator());
    }
  }
}
