package com.boostercloud.cli.services;

import com.boostercloud.cli.common.CliException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The logger adapter and the {@code errors.log} writer ({@code services/logger.ts}). The {@code oraLogger}
 * singleton of TypeScript is the {@link OraLogger} held by the {@code CliContext}.
 */
public final class Loggers {
  /** File, relative to the current directory, where the full error of every failed command is appended. */
  public static final String ERRORS_FILE = "errors.log";

  private static final DateTimeFormatter ISO_MILLIS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private Loggers() {}

  /**
   * Adapts the spinner logger to the {@link Logger} interface: {@code debug} and {@code warn} print a warning,
   * {@code info} an informative line and {@code error} a failure line.
   *
   * @param oraLogger the spinner logger
   * @return the adapter
   */
  public static Logger logger(OraLogger oraLogger) {
    return new Logger() {
      @Override
      public void debug(String message) {
        oraLogger.warn(message);
      }

      @Override
      public void info(String message) {
        oraLogger.info(message);
      }

      @Override
      public void warn(String message) {
        oraLogger.warn(message);
      }

      @Override
      public void error(String message) {
        oraLogger.fail(message);
      }
    };
  }

  /**
   * Appends some data to {@code <cwd>/errors.log}, prefixing every line with {@code [<ISO-8601 timestamp>] }. No
   * trailing newline is written, as in TypeScript.
   *
   * @param cwd the current directory
   * @param clock the clock used for the timestamps
   * @param data the text to append
   */
  public static void appendOnErrorsFile(Path cwd, Clock clock, String data) {
    Path errorsFile = cwd.resolve(ERRORS_FILE);
    String transformedData =
        Arrays.stream(data.split("\n", -1))
            .map(line -> "[" + ISO_MILLIS.format(clock.instant()) + "] " + line)
            .collect(Collectors.joining("\n"));
    try {
      Files.writeString(
          errorsFile, transformedData, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new CliException("Could not write the errors file " + errorsFile + ": " + e.getMessage(), e);
    }
  }
}
