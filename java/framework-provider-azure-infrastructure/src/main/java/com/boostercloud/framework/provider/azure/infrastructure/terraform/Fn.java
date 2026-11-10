package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import com.boostercloud.framework.types.BoosterJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Terraform function calls ({@code Fn} of CDK for Terraform) and the rendering of raw values as HCL expressions inside
 * a {@code ${...}} interpolation, as CDK for Terraform's {@code TFExpression} does:
 *
 * <ul>
 *   <li>maps become object constructors {@code {"key" = value, ...}} (keys in insertion order);
 *   <li>lists become tuples {@code [a, b]};
 *   <li>a string that is exactly one reference ({@code ${azurerm_x.y.attr}}) becomes the bare traversal
 *       {@code azurerm_x.y.attr};
 *   <li>other strings become quoted templates in which embedded references stay interpolations
 *       ({@code "AccountEndpoint=https://${azurerm_cosmosdb_account.x.name}.documents..."});
 *   <li>numbers and booleans are written as literals.
 * </ul>
 */
public final class Fn {

  private Fn() {}

  /**
   * {@code Fn.merge([...maps])}: the interpolation {@code ${merge(<map>, <map>...)}}.
   *
   * @param maps the maps (raw values, possibly containing references)
   * @return the Terraform expression string
   */
  public static String merge(List<?> maps) {
    StringJoiner arguments = new StringJoiner(", ");
    for (Object map : maps) {
      arguments.add(renderExpression(map));
    }
    return "${merge(" + arguments + ")}";
  }

  /**
   * Renders a raw value as an HCL expression (the text between {@code ${} and {@code }}).
   *
   * @param value a raw value
   * @return the expression
   */
  public static String renderExpression(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String text) {
      return renderString(text);
    }
    if (value instanceof Boolean bool) {
      return bool.toString();
    }
    if (value instanceof Double || value instanceof Float) {
      return BoosterJson.formatNumber(((Number) value).doubleValue());
    }
    if (value instanceof Number number) {
      return number.toString();
    }
    if (value instanceof Map<?, ?> map) {
      StringJoiner items = new StringJoiner(", ", "{", "}");
      map.forEach((key, item) -> items.add("\"" + escapeLiteral(String.valueOf(key)) + "\" = " + renderExpression(item)));
      return items.toString();
    }
    if (value instanceof List<?> list) {
      StringJoiner items = new StringJoiner(", ", "[", "]");
      for (Object item : list) {
        items.add(renderExpression(item));
      }
      return items.toString();
    }
    return renderString(value.toString());
  }

  private static String renderString(String text) {
    List<int[]> tokens = interpolations(text);
    if (tokens.size() == 1 && tokens.get(0)[0] == 0 && tokens.get(0)[1] == text.length()) {
      return text.substring(2, text.length() - 1);
    }
    StringBuilder rendered = new StringBuilder("\"");
    int position = 0;
    for (int[] token : tokens) {
      rendered.append(escapeLiteral(text.substring(position, token[0])));
      rendered.append(text, token[0], token[1]);
      position = token[1];
    }
    rendered.append(escapeLiteral(text.substring(position)));
    return rendered.append('"').toString();
  }

  /**
   * @param text a string
   * @return the {@code [start, end)} ranges of its {@code ${...}} interpolations (braces and quoted strings inside an
   *     interpolation are balanced)
   */
  static List<int[]> interpolations(String text) {
    List<int[]> tokens = new ArrayList<>();
    int index = 0;
    while (true) {
      int start = text.indexOf("${", index);
      if (start < 0) {
        return tokens;
      }
      if (start > 0 && text.charAt(start - 1) == '$') {
        // "$${" is an escaped literal
        index = start + 2;
        continue;
      }
      int end = matchingBrace(text, start + 2);
      if (end < 0) {
        return tokens;
      }
      tokens.add(new int[] {start, end + 1});
      index = end + 1;
    }
  }

  private static int matchingBrace(String text, int from) {
    int depth = 0;
    boolean inString = false;
    for (int i = from; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (c == '\\') {
          i++;
        } else if (c == '"') {
          inString = false;
        }
      } else if (c == '"') {
        inString = true;
      } else if (c == '{') {
        depth++;
      } else if (c == '}') {
        if (depth == 0) {
          return i;
        }
        depth--;
      }
    }
    return -1;
  }

  private static String escapeLiteral(String literal) {
    StringBuilder escaped = new StringBuilder(literal.length());
    for (int i = 0; i < literal.length(); i++) {
      char c = literal.charAt(i);
      switch (c) {
        case '\\' -> escaped.append("\\\\");
        case '"' -> escaped.append("\\\"");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        case '%' -> escaped.append(i + 1 < literal.length() && literal.charAt(i + 1) == '{' ? "%%" : "%");
        default -> escaped.append(c);
      }
    }
    return escaped.toString();
  }
}
