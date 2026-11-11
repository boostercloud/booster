package com.boostercloud.framework.provider.azure.helpers;

import java.util.regex.Pattern;

/** JavaScript's {@code Number(string)} conversion, used where TypeScript converts app settings to numbers. */
public final class JsNumbers {

  private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");
  private static final Pattern HEX = Pattern.compile("0[xX][0-9a-fA-F]+");
  private static final Pattern INFINITY = Pattern.compile("[+-]?Infinity");

  private JsNumbers() {}

  /**
   * {@code Number(text)}: {@code ""} is 0, decimal and hexadecimal literals and {@code Infinity} are parsed, anything
   * else is {@code NaN}.
   *
   * @param text the text, or {@code null}
   * @return the number ({@code NaN} for {@code null})
   */
  public static double number(String text) {
    if (text == null) {
      return Double.NaN;
    }
    String trimmed = text.strip();
    if (trimmed.isEmpty()) {
      return 0;
    }
    if (DECIMAL.matcher(trimmed).matches()) {
      return Double.parseDouble(trimmed);
    }
    if (HEX.matcher(trimmed).matches()) {
      return Long.parseLong(trimmed.substring(2), 16);
    }
    if (INFINITY.matcher(trimmed).matches()) {
      return trimmed.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    }
    return Double.NaN;
  }
}
