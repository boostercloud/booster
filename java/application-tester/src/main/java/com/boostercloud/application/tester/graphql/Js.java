package com.boostercloud.application.tester.graphql;

import com.boostercloud.framework.types.BoosterJson;
import java.util.Collection;

/** JavaScript value semantics needed to reproduce Apollo and subscriptions-transport-ws decisions on raw values. */
final class Js {

  private Js() {}

  /**
   * @param value a raw value
   * @return its JavaScript truthiness ({@code null}, {@code false}, {@code ""}, {@code 0} and {@code NaN} are falsy;
   *     objects and arrays, even empty, are truthy)
   */
  static boolean truthy(Object value) {
    return switch (value) {
      case null -> false;
      case Boolean bool -> bool;
      case String string -> !string.isEmpty();
      case Double number -> number != 0 && !number.isNaN();
      case Float number -> number != 0 && !number.isNaN();
      case Number number -> number.doubleValue() != 0;
      default -> true;
    };
  }

  /**
   * @param value a raw value
   * @return {@code String(value)} for primitives ({@code null} → {@code "null"})
   */
  static String string(Object value) {
    return switch (value) {
      case null -> "null";
      case Double number -> BoosterJson.formatNumber(number);
      case Float number -> BoosterJson.formatNumber(number.doubleValue());
      case Collection<?> collection -> String.join(",", collection.stream().map(Js::string).toList());
      default -> String.valueOf(value);
    };
  }
}
