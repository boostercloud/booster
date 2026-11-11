package com.boostercloud.framework.types;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.StringJoiner;

/**
 * The default {@link Logger}: the equivalent of Node's {@code console}. {@code debug} and {@code info} write to
 * {@link System#out}, {@code warn} and {@code error} to {@link System#err} (looked up on every call, so tests can
 * redirect them). Arguments are joined with a single space like {@code console.log}: strings as they are, throwables
 * with their stack trace, numbers like JavaScript prints them, and any other object as JSON (falling back to
 * {@link Object#toString()}).
 *
 * <p>Level gating is not done here but by {@code Loggers.getLogger}, exactly like in TypeScript.
 */
public final class ConsoleLogger implements Logger {

  /** The shared instance. */
  public static final ConsoleLogger INSTANCE = new ConsoleLogger();

  private ConsoleLogger() {}

  @Override
  public void debug(Object message, Object... optionalParams) {
    write(System.out, message, optionalParams);
  }

  @Override
  public void info(Object message, Object... optionalParams) {
    write(System.out, message, optionalParams);
  }

  @Override
  public void warn(Object message, Object... optionalParams) {
    write(System.err, message, optionalParams);
  }

  @Override
  public void error(Object message, Object... optionalParams) {
    write(System.err, message, optionalParams);
  }

  private static void write(PrintStream stream, Object message, Object... optionalParams) {
    stream.println(format(message, optionalParams));
  }

  /**
   * Formats a log line like {@code console.log(message, ...optionalParams)}.
   *
   * @param message the first argument
   * @param optionalParams the other arguments
   * @return the line that would be printed
   */
  public static String format(Object message, Object... optionalParams) {
    StringJoiner joiner = new StringJoiner(" ");
    joiner.add(render(message));
    if (optionalParams != null) {
      for (Object parameter : optionalParams) {
        joiner.add(render(parameter));
      }
    }
    return joiner.toString();
  }

  private static String render(Object value) {
    return switch (value) {
      case null -> "null";
      case String string -> string;
      case Throwable throwable -> {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        yield writer.toString().stripTrailing();
      }
      case Double number -> BoosterJson.formatNumber(number);
      case Float number -> BoosterJson.formatNumber(number.doubleValue());
      case Number number -> number.toString();
      case Boolean bool -> bool.toString();
      case Character character -> character.toString();
      case Enum<?> enumValue -> enumValue.name();
      default -> {
        try {
          yield BoosterJson.toJson(value);
        } catch (RuntimeException e) {
          yield String.valueOf(value);
        }
      }
    };
  }
}
