package com.boostercloud.integration.common;

import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.Logger;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The application's logger ({@code src/common/custom-logger.ts}): one line per call on the standard output,
 * {@code [LEVEL]: message}, with the time and ANSI colours when {@code BOOSTER_ENV} is {@code local}.
 *
 * <p>As in TypeScript, the extra parameters are formatted as <em>one</em> argument (the array of them), so a call
 * through {@code Loggers.getLogger} prints {@code [Booster]|Location:  [...]}; real newlines and the two characters
 * {@code \n} become spaces and runs of whitespace collapse to one space. The array is rendered by
 * {@link ConsoleLogger#format} (JSON) instead of Node's {@code util.inspect}.
 */
public class CustomLogger implements Logger {

  private static final Pattern NEW_LINES = Pattern.compile("\n|\\\\n");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final PrintStream out;

  /** A logger writing to {@link System#out}. */
  public CustomLogger() {
    this(System.out);
  }

  /**
   * @param out where the lines go
   */
  public CustomLogger(PrintStream out) {
    this.out = Objects.requireNonNull(out, "out");
  }

  @Override
  public void debug(Object message, Object... optionalParams) {
    write(Level.DEBUG, message, optionalParams);
  }

  @Override
  public void info(Object message, Object... optionalParams) {
    write(Level.INFO, message, optionalParams);
  }

  @Override
  public void warn(Object message, Object... optionalParams) {
    write(Level.WARN, message, optionalParams);
  }

  @Override
  public void error(Object message, Object... optionalParams) {
    write(Level.ERROR, message, optionalParams);
  }

  private void write(Level logLevel, Object message, Object... optionalParams) {
    StringBuilder line = new StringBuilder();
    if (isLocal()) {
      line.append(logLevelColor(logLevel)).append(Dates.nowIso()).append(" - ");
    }
    line.append(logLevelMessage(logLevel));
    line.append(formatMessage(message, optionalParams));
    if (isLocal()) {
      line.append("\u001B[0m");
    }
    line.append('\n');
    // one write per line: Java logs from several threads at once
    out.print(line);
    out.flush();
  }

  /**
   * {@code util.format(message, optionalParams)} with the TypeScript quirk of passing the parameters as one array,
   * then newlines (real or escaped) as spaces and whitespace collapsed.
   *
   * @param message the message
   * @param optionalParams the other arguments
   * @return the formatted message
   */
  static String formatMessage(Object message, Object... optionalParams) {
    Object parameters = optionalParams == null ? java.util.List.of() : Arrays.asList(optionalParams);
    String formatted = ConsoleLogger.format(message, parameters);
    return WHITESPACE.matcher(NEW_LINES.matcher(formatted).replaceAll(" ")).replaceAll(" ");
  }

  static String logLevelColor(Level logLevel) {
    return switch (logLevel) {
      case ERROR -> "\u001B[31m";
      case WARN -> "\u001B[33m";
      case INFO -> "\u001B[34m";
      case DEBUG -> "\u001B[32m";
    };
  }

  static String logLevelMessage(Level logLevel) {
    String logLevelMessage = logLevel.name().toUpperCase(Locale.ROOT);
    if (isLocal()) {
      return logLevelColor(logLevel) + "[" + logLevelMessage + "]: ";
    }
    return "[" + logLevelMessage + "]: ";
  }

  private static boolean isLocal() {
    return "local".equals(EnvironmentVariables.boosterEnvironment().orElse(null));
  }
}
