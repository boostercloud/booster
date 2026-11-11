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
 * <p>The evaluator is the TypeScript one, a deliberately small subset of the provider filter language, kept as is:
 *
 * <ul>
 *   <li>only the <b>first</b> top-level filter property is evaluated (the others are ignored); no filter matches
 *       everything; {@code and}/{@code or}/{@code not} are read as property names and fail;</li>
 *   <li>the operators of one property are AND-ed in order: {@code eq}/{@code ne} (strict equality: no coercion, objects
 *       and arrays by reference), {@code lt}/{@code gt}/{@code gte}/{@code lte} (JavaScript relational comparison: a
 *       missing property passes), {@code in} (the operand includes the value), {@code contains} (string or array),
 *       {@code beginsWith} (string), {@code regex}/{@code iRegex} (unanchored search, Java regular expressions, with
 *       a bounded evaluation: a catastrophically backtracking pattern fails instead of pinning the thread);</li>
 *   <li>{@code includes} (array; an object operand matches items with those properties) and nested property filters
 *       decide alone: the remaining operators are not evaluated; any other key with a non-object operand (e.g.
 *       {@code isDefined}) is ignored.</li>
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

  /** {@code filterReadModel(readModel, filters)}: only the first property decides. */
  static boolean filterReadModel(Object readModel, Map<String, Object> filters) {
    if (filters == null) {
      return true;
    }
    for (Map.Entry<String, Object> filteredProp : filters.entrySet()) {
      Object readModelPropValue = property(readModel, filteredProp.getKey());
      return filterByOperation(filteredProp.getValue(), readModelPropValue);
    }
    return true;
  }

  /** {@code filterByOperation(filter, readModelPropValue)}. */
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
          return includes(readModelPropValue, value);
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
          if (isObject(value)) {
            return filterByOperation(value, property(readModelPropValue, operation));
          }
        }
      }
    }
    return true;
  }

  private static boolean regex(Object readModelPropValue, Object element, boolean ignoreCase) {
    if (readModelPropValue instanceof String text) {
      int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
      String source = jsString(element);
      try {
        return Pattern.compile(source, flags).matcher(new BoundedInput(text)).find();
      } catch (BoundedInput.Exhausted | StackOverflowError e) {
        throw new IllegalArgumentException("The regular expression /" + source + "/ is too complex to evaluate "
            + "against a value of " + text.length() + " characters; simplify it");
      }
    }
    return false;
  }

  /**
   * Java-only, a security measure: the subscriber's pattern runs on every change of the read model, so its evaluation
   * may read at most {@code max(10⁸, 4·n²)} characters of a value of {@code n} characters (never more than 10⁹), the
   * budget of the local provider's {@code JsRegExp}; beyond it (catastrophic backtracking) or the stack, the
   * subscription fails with an error instead of pinning the thread that stores the read model.
   */
  private static final class BoundedInput implements CharSequence {
    private static final class Exhausted extends RuntimeException {
      private static final long serialVersionUID = 1L;
      private static final Exhausted INSTANCE = new Exhausted();

      private Exhausted() {
        super(null, null, false, false);
      }
    }

    private final String value;
    private long remaining;

    BoundedInput(String value) {
      this.value = value;
      this.remaining = Math.min(1_000_000_000L, Math.max(100_000_000L, 4L * value.length() * value.length()));
    }

    @Override
    public char charAt(int index) {
      if (--remaining < 0) {
        throw Exhausted.INSTANCE;
      }
      return value.charAt(index);
    }

    @Override
    public int length() {
      return value.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return value.substring(start, end);
    }

    @Override
    public String toString() {
      return value;
    }
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
