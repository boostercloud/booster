package com.boostercloud.cli.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.services.process.CommandFailedException;
import com.boostercloud.cli.services.process.ProcessError;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code common/errors.ts} helpers. */
class ErrorsTest {
  @BeforeEach
  void noColors() {
    ColorSupport.setLevel(0);
  }

  @Test
  void wrapExecErrorAppendsTheProcessOutput() {
    var failed = new CommandFailedException("Command failed", "git init", 1, "out\n", "err", null);
    CliException wrapped = Errors.wrapExecError(new ProcessError(failed), "Could not initialize git repository");
    assertThat(wrapped).hasMessage("Could not initialize git repository\nout\nerr");
  }

  @Test
  void wrapExecErrorWithoutOutput() {
    assertThat(Errors.wrapExecError(new RuntimeException("x"), "Error when publishing stubs"))
        .hasMessage("Error when publishing stubs\n");
  }

  @Test
  void guardErrorPrefixesTheMessageWithTheErrorName() {
    CliException guarded = Errors.guardError("Project contains compilation errors").apply(new CliException("boom"));
    assertThat(guarded).hasMessage("[Error] Project contains compilation errors:\nboom");
    assertThat(Errors.guardError("Could not clean project").apply(new IllegalStateException("x")))
        .hasMessage("[IllegalStateException] Could not clean project:\nx");
  }

  @Test
  void unknownToErrorKeepsErrorsAndStringifiesTheRest() {
    RuntimeException error = new RuntimeException("x");
    assertThat(Errors.unknownToError(error)).isSameAs(error);
    assertThat(Errors.unknownToError("text")).hasMessage("\"text\"");
    assertThat(Errors.unknownToError(42)).hasMessage("42");
    assertThat(Errors.unknownToError(null)).hasMessage("null");
  }

  @Test
  void stackOfFormatsLikeNode() {
    String stack = Errors.stackOf(new CliException("outer", new IllegalStateException("inner")));
    assertThat(stack).startsWith("Error: outer\n    at ").contains("\nCaused by: IllegalStateException: inner");
  }

  @Test
  void printsErrorsLikeOclif() {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    Errors.printError(new PrintStream(bytes, true, StandardCharsets.UTF_8), " message\nsecond line");
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .isEqualTo("    Error: message" + System.lineSeparator() + "    second line" + System.lineSeparator());

    bytes.reset();
    Errors.printParseError(new PrintStream(bytes, true, StandardCharsets.UTF_8), "Flag --fields expects a value");
    assertThat(bytes.toString(StandardCharsets.UTF_8))
        .contains(" ›   Error: Flag --fields expects a value")
        .contains(" ›   See more help with --help");
  }

  @Test
  void exitCodeOfUsesTheChildProcessExitCode() {
    assertThat(ProcessExitException.exitCodeOf(new CliException("x"))).isEqualTo(1);
    assertThat(ProcessExitException.exitCodeOf(new CliException("x", new ProcessExitException("y", 7)))).isEqualTo(7);
  }
}
