package com.boostercloud.framework.core.services.pubsub;

import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.core.services.FilterHelpers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Port of {@code FilteredReadModelPubSub}: an in-memory pub-sub over the changed read models, yielding those whose
 * class and filters match the subscription.
 *
 * <p>{@link #asyncIterator} applies the read model's {@code before} hooks to the envelope (with the envelope's user),
 * keeps the read models whose class simple name is the (transformed) envelope's class name and that pass the in-memory
 * filter evaluator, and returns them in order.
 *
 * <p>The evaluator keeps the operator semantics of the TypeScript one, a small subset of the provider filter language,
 * but checks the <b>whole</b> filter. TypeScript returns the result of the first top-level property (and of the first
 * nested property, or of {@code includes}) and ignores the rest, so a subscription filter such as
 * {@code {id: {ne: ""}, userId: {eq: <me>}}} passed a before hook that validates {@code userId} and still received every
 * user's changes. Here:
 *
 * <ul>
 *   <li>every top-level property must match (AND); no filter matches everything; {@code and} (every filter of the
 *       list), {@code or} (at least one) and {@code not} combine filters, at the top level and inside nested property
 *       filters;</li>
 *   <li>every operator of one property must match: {@code eq}/{@code ne} (strict equality: no coercion, objects and
 *       arrays by reference), {@code lt}/{@code gt}/{@code gte}/{@code lte} (JavaScript relational comparison: a
 *       missing property passes), {@code in} (the operand includes the value), {@code contains} (string or array),
 *       {@code beginsWith} (string), {@code includes} (array; an object operand matches items with those properties),
 *       {@code regex}/{@code iRegex} (unanchored search, Java regular expressions), {@code isDefined} (the property
 *       exists and is not {@code null}, as the providers store no nulls);</li>
 *   <li>a nested property filter (every one of its properties must match) on a missing or {@code null} object fails,
 *       as in TypeScript; any other key with a non-object operand is ignored.</li>
 * </ul>
 *
 * Read model properties are read from the instance (record components, fields, calculated fields) or the map, and
 * compared in their JSON form ({@code UUID}s and dates as strings).
 */
public class FilteredReadModelPubSub implements ReadModelPubSub {

  /** JavaScript's {@code undefined}: a property that does not exist. */
  static final Object UNDEFINED = new Object() {
    @Override
    public String toString() {
      return "undefined";
    }
  };

  private static final Pattern JS_NUMBER =
      Pattern.compile("[+-]?(?:(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?|Infinity)");

  private final List<Object> readModels;

  /**
   * @param readModels the changed read model instances
   */
  public FilteredReadModelPubSub(List<Object> readModels) {
    this.readModels = List.copyOf(readModels);
  }

  /**
   * @return the changed read models
   */
  public List<Object> readModels() {
    return readModels;
  }

  @Override
  public Iterator<Object> asyncIterator(ReadModelRequestEnvelope readModelRequestEnvelope, BoosterConfig config) {
    String readModelName = className(readModelRequestEnvelope);
    ReadModelMetadata readModelMetadata = config.readModels.get(readModelName);
    if (readModelMetadata == null) {
      throw new IllegalStateException("Could not find the read model " + readModelName);
    }
    ReadModelRequestEnvelope newReadModelRequestEnvelope = FilterHelpers.applyReadModelRequestBeforeFunctions(
        readModelRequestEnvelope, readModelMetadata.before(), readModelRequestEnvelope.currentUser());
    String newReadModelName = className(newReadModelRequestEnvelope);
    List<Object> matching = new ArrayList<>();
    for (Object readModel : readModels) {
      if (readModel.getClass().getSimpleName().equals(newReadModelName)
          && filterReadModel(readModel, newReadModelRequestEnvelope.filters())) {
        matching.add(readModel);
      }
    }
    return matching.iterator();
  }

  private static String className(ReadModelRequestEnvelope envelope) {
    return envelope.type() != null ? envelope.type().getSimpleName() : envelope.className();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // The in-memory filter evaluator (read-model-pub-sub.ts)
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code filterReadModel(readModel, filters)}: every property and combinator must match (TypeScript: only the first
   * property decides).
   */
  static boolean filterReadModel(Object readModel, Map<String, Object> filters) {
    if (filters == null) {
      return true;
    }
    for (Map.Entry<String, Object> filteredProp : filters.entrySet()) {
      Object filter = jsValue(filteredProp.getValue());
      boolean matches = switch (filteredProp.getKey()) {
        case "and" -> filterList(filter).stream().allMatch(item -> filterReadModel(readModel, filterMap(item)));
        case "or" -> filterList(filter).stream().anyMatch(item -> filterReadModel(readModel, filterMap(item)));
        case "not" -> !filterReadModel(readModel, filterMap(filter));
        default -> filterByOperation(filter, property(readModel, filteredProp.getKey()));
      };
      if (!matches) {
        return false;
      }
    }
    return true;
  }

  /** {@code filterByOperation(filter, readModelPropValue)}: every operator, nested property and combinator must match. */
  static boolean filterByOperation(Object filter, Object readModelPropValue) {
    for (Map.Entry<String, Object> entry : entries(filter)) {
      String operation = entry.getKey();
      Object value = jsValue(entry.getValue());
      switch (operation) {
        case "eq" -> {
          if (!strictEquals(readModelPropValue, value)) {
            return false;
          }
        }
        case "ne" -> {
          if (strictEquals(readModelPropValue, value)) {
            return false;
          }
        }
        case "lt" -> {
          if (compareIs(readModelPropValue, value, Relation.GREATER_OR_EQUAL)) {
            return false;
          }
        }
        case "gt" -> {
          if (compareIs(readModelPropValue, value, Relation.LESS_OR_EQUAL)) {
            return false;
          }
        }
        case "gte" -> {
          if (compareIs(readModelPropValue, value, Relation.LESS)) {
            return false;
          }
        }
        case "lte" -> {
          if (compareIs(readModelPropValue, value, Relation.GREATER)) {
            return false;
          }
        }
        case "in" -> {
          if (!valueIncludes(value, readModelPropValue)) {
            return false;
          }
        }
        case "contains" -> {
          if (!contains(readModelPropValue, value)) {
            return false;
          }
        }
        case "beginsWith" -> {
          if (!beginWith(readModelPropValue, value)) {
            return false;
          }
        }
        case "includes" -> {
          if (!includes(readModelPropValue, value)) {
            return false;
          }
        }
        case "isDefined" -> {
          if (isDefined(readModelPropValue) != Js.isTruthy(value)) {
            return false;
          }
        }
        case "and" -> {
          if (!filterList(value).stream().allMatch(item -> filterByOperation(item, readModelPropValue))) {
            return false;
          }
        }
        case "or" -> {
          if (filterList(value).stream().noneMatch(item -> filterByOperation(item, readModelPropValue))) {
            return false;
          }
        }
        case "not" -> {
          if (filterByOperation(value, readModelPropValue)) {
            return false;
          }
        }
        case "regex" -> {
          if (!regex(readModelPropValue, value, false)) {
            return false;
          }
        }
        case "iRegex" -> {
          if (!regex(readModelPropValue, value, true)) {
            return false;
          }
        }
        default -> {
          if (isObject(value) && !filterByOperation(value, property(readModelPropValue, operation))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /** The filters of {@code and}/{@code or}: a list (a single filter object counts as a list of one). */
  private static List<?> filterList(Object value) {
    return switch (value) {
      case List<?> list -> list;
      case Map<?, ?> map -> List.of(map);
      case null, default -> throw new IllegalArgumentException("Cannot read the filters of " + jsString(value)
          + ": a list of filters was expected");
    };
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> filterMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalArgumentException("Cannot convert " + jsString(value) + " to a filter object");
  }

  /** The property exists and holds a value (the providers do not store {@code null}s). */
  private static boolean isDefined(Object readModelPropValue) {
    return readModelPropValue != null && readModelPropValue != UNDEFINED;
  }

  private static boolean regex(Object readModelPropValue, Object element, boolean ignoreCase) {
    if (readModelPropValue instanceof String text) {
      int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
      return Pattern.compile(jsString(element), flags).matcher(text).find();
    }
    return false;
  }

  private static boolean contains(Object readModelPropValue, Object element) {
    return switch (readModelPropValue) {
      case List<?> list -> arrayIncludes(list, element);
      case String text -> text.contains(jsString(element));
      case null, default -> false;
    };
  }

  private static boolean beginWith(Object readModelPropValue, Object element) {
    return readModelPropValue instanceof String text && text.startsWith(jsString(element));
  }

  private static boolean includes(Object readModelPropValue, Object element) {
    if (readModelPropValue instanceof List<?> list) {
      // Check that array includes an object
      if (isObject(element)) {
        List<Map.Entry<String, Object>> expected = entries(element);
        for (Object item : list) {
          boolean matches = true;
          for (Map.Entry<String, Object> key : expected) {
            if (!strictEquals(property(jsValue(item), key.getKey()), key.getValue())) {
              matches = false;
              break;
            }
          }
          if (matches) {
            return true;
          }
        }
        return false;
      }
      // If not, do a regular includes
      return arrayIncludes(list, element);
    }
    return false;
  }

  /** The {@code in} operator: {@code value.includes(readModelPropValue)}. */
  private static boolean valueIncludes(Object value, Object readModelPropValue) {
    return switch (value) {
      case List<?> list -> arrayIncludes(list, readModelPropValue);
      case String text -> text.contains(jsString(readModelPropValue));
      case null -> throw new IllegalArgumentException("Cannot read properties of null (reading 'includes')");
      default -> throw new IllegalArgumentException("value.includes is not a function");
    };
  }

  /** {@code Array.prototype.includes}: SameValueZero. */
  private static boolean arrayIncludes(List<?> list, Object element) {
    for (Object item : list) {
      Object value = jsValue(item);
      if (strictEquals(value, element) || (isNaN(value) && isNaN(element))) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // JavaScript value semantics
  // ---------------------------------------------------------------------------------------------------------------

  /** {@code typeof value === 'object'} ({@code null} included). */
  private static boolean isObject(Object value) {
    return value == null || value instanceof Map<?, ?> || value instanceof List<?>;
  }

  /** {@code Object.entries(value)}. */
  @SuppressWarnings("unchecked")
  private static List<Map.Entry<String, Object>> entries(Object value) {
    List<Map.Entry<String, Object>> entries = new ArrayList<>();
    if (value == null || value == UNDEFINED) {
      throw new IllegalArgumentException("Cannot convert undefined or null to object");
    }
    switch (value) {
      case Map<?, ?> map -> {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          entries.add(new AbstractMap.SimpleEntry<>(String.valueOf(entry.getKey()), (Object) entry.getValue()));
        }
      }
      case List<?> list -> {
        for (int i = 0; i < list.size(); i++) {
          entries.add(new AbstractMap.SimpleEntry<>(Integer.toString(i), list.get(i)));
        }
      }
      case String text -> {
        for (int i = 0; i < text.length(); i++) {
          entries.add(new AbstractMap.SimpleEntry<>(Integer.toString(i), String.valueOf(text.charAt(i))));
        }
      }
      default -> {
        // Numbers and booleans have no own enumerable properties
      }
    }
    return entries;
  }

  /** {@code value[key]}, {@link #UNDEFINED} when absent; {@code undefined[key]} and {@code null[key]} fail. */
  static Object property(Object value, String key) {
    if (value == null || value == UNDEFINED) {
      throw new IllegalArgumentException("Cannot read properties of " + value + " (reading '" + key + "')");
    }
    return switch (value) {
      case Map<?, ?> map -> map.containsKey(key) ? jsValue(map.get(key)) : UNDEFINED;
      case List<?> list -> {
        Integer index = arrayIndex(key);
        if (index != null && index < list.size()) {
          yield jsValue(list.get(index));
        }
        yield key.equals("length") ? (Object) list.size() : UNDEFINED;
      }
      case String text -> {
        Integer index = arrayIndex(key);
        if (index != null && index < text.length()) {
          yield String.valueOf(text.charAt(index));
        }
        yield key.equals("length") ? (Object) text.length() : UNDEFINED;
      }
      case Number ignored -> UNDEFINED;
      case Boolean ignored -> UNDEFINED;
      default -> {
        Optional<PropertyMetadata> metadata = ClassMetadata.of(value.getClass()).field(key);
        yield metadata.isPresent() ? jsValue(UserCode.await(metadata.get().readValue(value))) : UNDEFINED;
      }
    };
  }

  private static Integer arrayIndex(String key) {
    if (key.isEmpty() || key.length() > 9 || !key.chars().allMatch(Character::isDigit) || (key.length() > 1 && key.charAt(0) == '0')) {
      return null;
    }
    return Integer.parseInt(key);
  }

  /** The JSON form of a Java value (raw values are kept, instances become maps, ids and dates strings). */
  private static Object jsValue(Object value) {
    return switch (value) {
      case null -> null;
      case String text -> text;
      case Number number -> number;
      case Boolean bool -> bool;
      case Map<?, ?> map -> map;
      case List<?> list -> list;
      default -> value == UNDEFINED ? UNDEFINED : BoosterJson.toRaw(value);
    };
  }

  /** {@code a === b}. */
  private static boolean strictEquals(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) {
      return x.doubleValue() == y.doubleValue();
    }
    if (a instanceof String || a instanceof Boolean) {
      return a.equals(b);
    }
    return a == b;
  }

  private static boolean isNaN(Object value) {
    return value instanceof Number number && Double.isNaN(number.doubleValue());
  }

  private enum Relation {
    LESS, LESS_OR_EQUAL, GREATER, GREATER_OR_EQUAL
  }

  /** JavaScript's {@code a < b}, {@code a <= b}, {@code a > b}, {@code a >= b}: {@code false} when {@code NaN} is involved. */
  private static boolean compareIs(Object a, Object b, Relation relation) {
    Object left = toPrimitive(a);
    Object right = toPrimitive(b);
    int comparison;
    if (left instanceof String x && right instanceof String y) {
      comparison = Integer.signum(x.compareTo(y));
    } else {
      double x = toNumber(left);
      double y = toNumber(right);
      if (Double.isNaN(x) || Double.isNaN(y)) {
        return false;
      }
      comparison = x < y ? -1 : (x > y ? 1 : 0);
    }
    return switch (relation) {
      case LESS -> comparison < 0;
      case LESS_OR_EQUAL -> comparison <= 0;
      case GREATER -> comparison > 0;
      case GREATER_OR_EQUAL -> comparison >= 0;
    };
  }

  private static Object toPrimitive(Object value) {
    return switch (value) {
      case Map<?, ?> ignored -> "[object Object]";
      case List<?> list -> Js.toString(list.stream().map(item -> item == null ? null : toPrimitive(jsValue(item))).toList());
      case null, default -> value;
    };
  }

  private static double toNumber(Object value) {
    if (value == UNDEFINED) {
      return Double.NaN;
    }
    return switch (value) {
      case null -> 0;
      case Number number -> number.doubleValue();
      case Boolean bool -> bool ? 1 : 0;
      case String text -> stringToNumber(text.strip());
      default -> Double.NaN;
    };
  }

  private static double stringToNumber(String text) {
    if (text.isEmpty()) {
      return 0;
    }
    try {
      if (text.startsWith("0x") || text.startsWith("0X")) {
        return Long.parseLong(text.substring(2), 16);
      }
      if (text.startsWith("0b") || text.startsWith("0B")) {
        return Long.parseLong(text.substring(2), 2);
      }
      if (text.startsWith("0o") || text.startsWith("0O")) {
        return Long.parseLong(text.substring(2), 8);
      }
      if (JS_NUMBER.matcher(text).matches()) {
        return Double.parseDouble(text);
      }
    } catch (NumberFormatException e) {
      return Double.NaN;
    }
    return Double.NaN;
  }

  /** {@code String(value)}. */
  private static String jsString(Object value) {
    return value == UNDEFINED ? "undefined" : Js.toString(value);
  }
}
