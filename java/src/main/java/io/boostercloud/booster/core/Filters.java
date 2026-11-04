package io.boostercloud.booster.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

/** Booster-style structured read-model filters. No Java source or SQL is evaluated. */
public final class Filters {
  private static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "gte", "lt", "lte", "in", "beginsWith", "contains", "regex", "iRegex", "includes", "isDefined");
  private Filters() {}
  public static boolean matches(JsonNode value, JsonNode filter) { return matches(value, filter, 0); }
  private static boolean matches(JsonNode value, JsonNode filter, int depth) {
    if (filter == null || filter.isNull()) return true;
    if (depth > 32 || !filter.isObject()) throw invalid("Filter must be an object with at most 32 nested levels");
    boolean result = true;
    for (Iterator<Map.Entry<String, JsonNode>> fields = filter.fields(); fields.hasNext();) {
      var field = fields.next(); var argument = field.getValue();
      boolean matched;
      switch (field.getKey()) {
        case "isDefined" -> {
          if (!argument.isBoolean()) throw invalid("isDefined requires a boolean");
          matched = (value != null && !value.isMissingNode()) == argument.booleanValue();
        }
        case "and", "or" -> {
          if (!argument.isArray()) throw invalid("and/or filters require an array");
          matched = field.getKey().equals("and");
          for (var child : argument) {
            boolean childResult = matches(value, child, depth + 1);
            matched = field.getKey().equals("and") ? matched && childResult : matched || childResult;
          }
        }
        case "not" -> matched = !matches(value, argument, depth + 1);
        default -> {
          JsonNode actual = path(value, field.getKey());
          if (!argument.isObject()) throw invalid("Each field filter must be an operator object");
          boolean operators = false, nested = false;
          var names = argument.fieldNames();
          while (names.hasNext()) { if (OPERATORS.contains(names.next())) operators = true; else nested = true; }
          if (operators && nested) {
            var nestedFilter = argument.deepCopy();
            var defined = ((com.fasterxml.jackson.databind.node.ObjectNode) nestedFilter).remove("isDefined");
            if (defined == null || !defined.isBoolean()) throw invalid("Unknown or mixed filter operator");
            var remainingNames = nestedFilter.fieldNames();
            while (remainingNames.hasNext()) if (OPERATORS.contains(remainingNames.next())) throw invalid("Unknown or mixed filter operator");
            // Object existence can accompany its nested typed fields, as in the baseline schema.
            boolean exists = !actual.isMissingNode() == defined.booleanValue();
            matched = matches(actual, nestedFilter, depth + 1) && exists;
          } else matched = operators ? operators(actual, argument) : matches(actual, argument, depth + 1);
        }
      }
      result &= matched;
    }
    return result;
  }
  static JsonNode path(JsonNode value, String path) {
    if (path.isBlank()) throw invalid("Empty filter field");
    JsonNode current = value;
    for (String key : path.split("\\.", -1)) {
      if (key.isEmpty()) throw invalid("Empty nested field");
      current = current == null ? MissingNode.getInstance() : current.path(key);
    }
    return current == null ? MissingNode.getInstance() : current;
  }
  private static boolean operators(JsonNode actual, JsonNode conditions) {
    boolean result = true;
    for (Iterator<Map.Entry<String, JsonNode>> fields = conditions.fields(); fields.hasNext();) {
      var field = fields.next(); var expected = field.getValue();
      boolean present = !actual.isMissingNode() && !actual.isNull();
      boolean matched = switch (field.getKey()) {
        case "eq" -> !actual.isMissingNode() && equal(actual, expected);
        case "ne" -> actual.isMissingNode() || !equal(actual, expected);
        case "isDefined" -> {
          if (!expected.isBoolean()) throw invalid("isDefined requires a boolean");
          yield !actual.isMissingNode() == expected.booleanValue();
        }
        case "gt", "gte", "lt", "lte" -> {
          requireComparable(expected);
          if (!present || !sameKind(actual, expected)) yield false;
          int compared = compare(actual, expected);
          yield switch (field.getKey()) { case "gt" -> compared > 0; case "gte" -> compared >= 0; case "lt" -> compared < 0; default -> compared <= 0; };
        }
        case "in" -> {
          if (!expected.isArray()) throw invalid("in requires an array");
          boolean found = false; for (var candidate : expected) found |= equal(actual, candidate); yield found;
        }
        case "includes" -> {
          if (!actual.isArray()) yield false;
          boolean found = false; for (var candidate : actual) found |= equal(candidate, expected); yield found;
        }
        case "beginsWith", "contains", "regex", "iRegex" -> {
          if (!expected.isTextual()) throw invalid("Text operators require a string");
          String operand = expected.textValue();
          if (field.getKey().endsWith("Regex") || field.getKey().equals("regex")) {
            if (operand.length() > 512) throw invalid("Regular expression exceeds 512 characters");
            try {
              var pattern = Pattern.compile(operand, field.getKey().equals("iRegex") ? Pattern.CASE_INSENSITIVE : 0);
              yield actual.isTextual() && pattern.matcher(actual.textValue()).find();
            } catch (PatternSyntaxException error) { throw invalid("Invalid regular expression"); }
          }
          yield actual.isTextual() && (field.getKey().equals("beginsWith") ? actual.textValue().startsWith(operand) : actual.textValue().contains(operand));
        }
        default -> throw invalid("Unknown filter operator: " + field.getKey());
      };
      result &= matched;
    }
    return result;
  }
  private static boolean equal(JsonNode first, JsonNode second) {
    return first.isNumber() && second.isNumber() ? first.decimalValue().compareTo(second.decimalValue()) == 0 : first.equals(second);
  }
  private static boolean sameKind(JsonNode a, JsonNode b) { return a.isNumber() && b.isNumber() || a.isTextual() && b.isTextual() || a.isBoolean() && b.isBoolean(); }
  private static void requireComparable(JsonNode value) { if (!value.isNumber() && !value.isTextual() && !value.isBoolean()) throw invalid("Comparison requires a number, string or boolean"); }
  static int compare(JsonNode first, JsonNode second) {
    boolean firstAbsent = first.isNull() || first.isMissingNode();
    boolean secondAbsent = second.isNull() || second.isMissingNode();
    if (firstAbsent || secondAbsent) return firstAbsent == secondAbsent ? 0 : firstAbsent ? -1 : 1;
    if (first.isNumber() && second.isNumber()) return first.decimalValue().compareTo(second.decimalValue());
    if (first.isBoolean() && second.isBoolean()) return Boolean.compare(first.booleanValue(), second.booleanValue());
    return first.asText().compareTo(second.asText());
  }
  private static BoosterException invalid(String message) { return new BoosterException(BoosterException.Code.VALIDATION, message); }
}
