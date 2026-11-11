package com.boostercloud.framework.core.internal;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.errors.BoosterErrors;
import java.util.Collection;
import java.util.Map;

/**
 * JavaScript value semantics that the TypeScript code relies on implicitly: truthiness ({@code if (x)},
 * {@code x || y}) and string conversion ({@code `${x}`}, {@code String(error)}).
 *
 * <p>Internal to framework-core: not part of the user-facing API.
 */
public final class Js {

  private Js() {}

  /**
   * JavaScript truthiness: {@code null}, {@code false}, {@code 0}, {@code NaN} and the empty string (also an empty
   * {@link UUID}) are falsy; everything else, including empty collections and maps, is truthy.
   *
   * @param value any value
   * @return whether JavaScript would consider it true
   */
  public static boolean isTruthy(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean bool -> bool;
      case CharSequence text -> !text.isEmpty();
      case UUID uuid -> uuid.value() != null && !uuid.value().isEmpty();
      case Double number -> number != 0 && !number.isNaN();
      case Float number -> number != 0 && !number.isNaN();
      case Number number -> number.doubleValue() != 0;
      default -> true;
    };
  }

  /**
   * {@code String(value)} / template-literal interpolation: numbers are written like JavaScript ({@code 123}, not
   * {@code 123.0}), {@code null} is {@code "null"}, errors are {@code "Name: message"} (see
   * {@link #errorToString(Throwable)}), lists are joined with commas, maps are {@code "[object Object]"}; any other
   * object uses its {@code toString()}.
   *
   * @param value any value
   * @return the JavaScript string
   */
  public static String toString(Object value) {
    return switch (value) {
      case null -> "null";
      case String text -> text;
      case Double number -> BoosterJson.formatNumber(number);
      case Float number -> BoosterJson.formatNumber(number.doubleValue());
      case Number number -> number.toString();
      case Throwable error -> errorToString(error);
      case Collection<?> list -> String.join(",", list.stream().map(item -> item == null ? "" : toString(item)).toList());
      case Map<?, ?> ignored -> "[object Object]";
      default -> value.toString();
    };
  }

  /**
   * The text JavaScript prints for an error ({@code String(error)}). Plain TypeScript {@code Error}s are ported as
   * {@link IllegalStateException}/{@link IllegalArgumentException} (ARCHITECTURE §7), so those print as
   * {@code "Error: <message>"}; every other throwable is {@code "<SimpleClassName>: <message>"}
   * ({@link BoosterErrors#errorToString(Throwable)}).
   *
   * @param error a throwable, or {@code null}
   * @return its JavaScript text ({@code "undefined"} for {@code null})
   */
  public static String errorToString(Throwable error) {
    if (error != null
        && (error.getClass() == IllegalStateException.class || error.getClass() == IllegalArgumentException.class)) {
      String message = error.getMessage() == null ? "" : error.getMessage();
      return message.isEmpty() ? "Error" : "Error: " + message;
    }
    return BoosterErrors.errorToString(error);
  }
}
