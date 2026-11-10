package com.boostercloud.rocket.webhook.core;

import com.boostercloud.framework.types.BoosterJson;

/**
 * The JavaScript semantics the TypeScript rocket relies on ({@code if (value)}, {@code `${value}`}). Internal helper of
 * the rocket, public only because the rocket's packages share it.
 */
public final class Js {

  private Js() {}

  /**
   * JavaScript truthiness: {@code null}, {@code false}, zero and {@code NaN} numbers and the empty string are falsy;
   * everything else (empty maps and lists included) is truthy.
   *
   * @param value a value
   * @return whether {@code if (value)} would run
   */
  public static boolean isTruthy(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean bool -> bool;
      case CharSequence text -> !text.isEmpty();
      case Number number -> {
        double asDouble = number.doubleValue();
        yield asDouble != 0 && !Double.isNaN(asDouble);
      }
      default -> true;
    };
  }

  /**
   * {@code `${value}`} for the values the rocket interpolates: {@code null} is {@code undefined} (the TypeScript
   * value is a missing property), numbers are printed like JavaScript.
   *
   * @param value a value
   * @return its text
   */
  public static String string(Object value) {
    return switch (value) {
      case null -> "undefined";
      case Double number -> BoosterJson.formatNumber(number);
      case Float number -> BoosterJson.formatNumber(number.doubleValue());
      default -> String.valueOf(value);
    };
  }
}
