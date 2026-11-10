package com.boostercloud.cli.common;

import com.boostercloud.cli.services.process.CommandFailedException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

/** Error helpers ({@code common/errors.ts}). */
public final class Errors {
  private Errors() {}

  /**
   * Builds an error extracting its message from the standard output and error of a failed process, if present.
   *
   * <p>TypeScript concatenates the {@code stdout} and {@code stderr} properties of an execa error (printing
   * {@code undefined} when they are missing); here missing outputs are empty strings.
   *
   * @param e the original error
   * @param prefix the text shown (dangerized) before the process output
   * @return the new error, whose cause is {@code e}
   */
  public static CliException wrapExecError(Throwable e, String prefix) {
    String stdout = "";
    String stderr = "";
    Throwable current = e;
    while (current != null) {
      if (current instanceof CommandFailedException failed) {
        stdout = failed.stdout();
        stderr = failed.stderr();
        break;
      }
      current = current.getCause();
    }
    return new CliException(Brand.dangerize(prefix) + "\n" + stdout + stderr, e);
  }

  /**
   * Returns the function that turns a failed operation into a fatal CLI error prefixed with the given text
   * ({@code guardError} in TypeScript, an effect {@code orDieWith}).
   *
   * <p>The message is {@code dangerize("[<error name>] <prefix>:") + "\n" + <error message>}.
   *
   * @param prefix the description of the failed operation, e.g. {@code Project contains compilation errors}
   * @return a function from the underlying error to the error that should be thrown
   */
  public static Function<Throwable, CliException> guardError(String prefix) {
    return err ->
        new CliException(Brand.dangerize("[" + errorName(err) + "] " + prefix + ":") + "\n" + messageOf(err), err);
  }

  /**
   * Converts an unknown value to an error. If it is not an error already, it is stringified as JSON.
   *
   * @param reason anything that was thrown or used as a rejection reason
   * @return {@code reason} itself when it is a {@link Throwable}, otherwise a new {@link CliException}
   */
  public static Throwable unknownToError(Object reason) {
    if (reason instanceof Throwable throwable) {
      return throwable;
    }
    return new CliException(stringify(reason));
  }

  /**
   * The TypeScript-style name of an error: {@link CliException#name()} for CLI errors and the simple class name
   * for any other exception.
   *
   * @param error the error
   * @return its name
   */
  public static String errorName(Throwable error) {
    if (error instanceof CliException cli) {
      return cli.name();
    }
    return error.getClass().getSimpleName();
  }

  /**
   * The message of an error, never {@code null}.
   *
   * @param error the error
   * @return its message, or its name when it has none
   */
  public static String messageOf(Throwable error) {
    String message = error.getMessage();
    return message == null ? errorName(error) : message;
  }

  /**
   * Formats an error like a Node.js stack trace ({@code error.stack}): a {@code Name: message} header followed by
   * {@code "    at ..."} frames, and then every cause.
   *
   * @param error the error
   * @return the formatted stack
   */
  public static String stackOf(Throwable error) {
    StringBuilder sb = new StringBuilder();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = error;
    boolean first = true;
    while (current != null && seen.add(current)) {
      if (!first) {
        sb.append("\nCaused by: ");
      }
      sb.append(header(current));
      for (StackTraceElement frame : current.getStackTrace()) {
        sb.append("\n    at ").append(frame);
      }
      first = false;
      current = current.getCause();
    }
    return sb.toString();
  }

  /**
   * Prints an error the way oclif's error handler does: {@code Error: <message>}, every line indented.
   *
   * @param err the standard error
   * @param message the message (a leading space is ignored)
   */
  public static void printError(java.io.PrintStream err, String message) {
    String text = "Error: " + message.stripLeading();
    for (String line : text.split("\n", -1)) {
      err.println("    " + line);
    }
    err.flush();
  }

  /**
   * Prints a command-line parsing error the way oclif does: {@code  ›   Error: <message>} followed by a hint.
   *
   * @param err the standard error
   * @param message the message
   */
  public static void printParseError(java.io.PrintStream err, String message) {
    String bang = ColorSupport.red("›");
    String text = "Error: " + message + "\nSee more help with --help";
    for (String line : text.split("\n", -1)) {
      err.println(" " + bang + "   " + line);
    }
    err.flush();
  }

  private static String header(Throwable error) {
    String message = error.getMessage();
    String name = errorName(error);
    return message == null || message.isEmpty() ? name : name + ": " + message;
  }

  private static String stringify(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String s) {
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    return String.valueOf(value);
  }
}
