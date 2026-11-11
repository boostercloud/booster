package com.boostercloud.framework.provider.local.store;

import com.boostercloud.framework.types.BoosterJson;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Port of NeDB's {@code lib/model.js} ({@code @seald-io/nedb} 4.0.2): deep copies, field-name validation, dot-path
 * access, the cross-type equality and ordering rules, query matching and update modifiers.
 *
 * <p>Values are raw JSON trees (see {@link Js}). A missing field is {@link Undefined#INSTANCE}, never {@code null}.
 * Numbers compare as JavaScript numbers (doubles), strings by UTF-16 code units ({@link String#compareTo}).
 *
 * <p>Query operators: comparison {@code $lt}, {@code $lte}, {@code $gt}, {@code $gte}, {@code $ne}, {@code $in},
 * {@code $nin}, {@code $regex}, {@code $exists}, {@code $size}, {@code $elemMatch}; logical {@code $and},
 * {@code $or}, {@code $not}, {@code $where} (a {@link Predicate} over the document). Update modifiers: {@code $set},
 * {@code $unset}, {@code $inc}, {@code $min}, {@code $max}, {@code $push} (with {@code $each}/{@code $slice}),
 * {@code $addToSet} (with {@code $each}), {@code $pop}, {@code $pull}.
 */
public final class Model {

  /** Shorthand for {@link Undefined#INSTANCE}. */
  public static final Object UNDEFINED = Undefined.INSTANCE;

  private Model() {}

  // ---------------------------------------------------------------------------------------------------------------
  // Copies and validation
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code deepCopy(obj)}: copies maps and lists (keeping key order), keeps primitives and dates, drops keys whose value
   * is undefined, turns other objects into raw values ({@code BoosterJson.toRaw}, the counterpart of NeDB flattening
   * class instances), {@link Instant}/{@link Date} into {@link NedbDate}s, regular expressions into {@code {}} and
   * functions into undefined. Undefined array elements become {@code null}, as they would after a write and a read.
   *
   * @param obj a value
   * @return the copy
   */
  public static Object deepCopy(Object obj) {
    return deepCopy(obj, false);
  }

  /**
   * {@code deepCopy(obj, strictKeys)}.
   *
   * @param obj a value
   * @param strictKeys whether keys starting with {@code $} or containing {@code .} are dropped
   * @return the copy
   */
  public static Object deepCopy(Object obj, boolean strictKeys) {
    if (obj == null || obj instanceof Boolean || obj instanceof Number || obj instanceof String
        || obj instanceof NedbDate || obj == UNDEFINED) {
      return obj;
    }
    if (obj instanceof Instant instant) {
      return NedbDate.of(instant);
    }
    if (obj instanceof Date date) {
      return NedbDate.of(date);
    }
    if (obj instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        if (strictKeys && (key.startsWith("$") || key.contains("."))) {
          continue;
        }
        Object value = deepCopy(entry.getValue(), strictKeys);
        if (value != UNDEFINED) {
          copy.put(key, value);
        }
      }
      return copy;
    }
    if (obj instanceof Iterable<?> iterable) {
      List<Object> copy = new ArrayList<>();
      for (Object element : iterable) {
        Object value = deepCopy(element, strictKeys);
        copy.add(value == UNDEFINED ? null : value);
      }
      return copy;
    }
    if (obj instanceof Object[] array) {
      return deepCopy(java.util.Arrays.asList(array), strictKeys);
    }
    if (obj instanceof JsRegExp || obj instanceof Pattern) {
      return new LinkedHashMap<String, Object>();
    }
    if (obj instanceof Predicate<?>) {
      return UNDEFINED;
    }
    return deepCopy(BoosterJson.toRaw(obj), strictKeys);
  }

  /**
   * @param document a document
   * @return a deep copy as a map
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> copyDocument(Map<String, Object> document) {
    return (Map<String, Object>) deepCopy(document);
  }

  /**
   * {@code checkKey(k, v)}: field names cannot begin with {@code $} (except NeDB's own {@code $$date},
   * {@code $$deleted}, {@code $$indexCreated} and {@code $$indexRemoved} markers) and cannot contain {@code .}.
   *
   * @param key the field name
   * @param value its value
   * @throws NedbError {@code Field names cannot begin with the $ character} / {@code Field names cannot contain a .}
   */
  public static void checkKey(String key, Object value) {
    if (key.startsWith("$")
        && !(key.equals("$$date") && value instanceof Number)
        && !(key.equals("$$deleted") && Boolean.TRUE.equals(value))
        && !key.equals("$$indexCreated")
        && !key.equals("$$indexRemoved")) {
      throw new NedbError("Field names cannot begin with the $ character");
    }
    if (key.contains(".")) {
      throw new NedbError("Field names cannot contain a .");
    }
  }

  /**
   * {@code checkObject(obj)}: {@link #checkKey} on every field of the tree.
   *
   * @param obj a value
   */
  public static void checkObject(Object obj) {
    if (obj instanceof List<?> list) {
      for (Object element : list) {
        checkObject(element);
      }
    }
    if (obj instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        checkKey(String.valueOf(entry.getKey()), entry.getValue());
        checkObject(entry.getValue());
      }
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Dot paths
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * {@code getDotValue(obj, field)}: follows a dot path ({@code "value.items.0.sku"}). When an intermediate value is an
   * array, a numeric next segment indexes it; otherwise the rest of the path is mapped over the elements and an array
   * of results is returned. A falsy intermediate value gives undefined.
   *
   * @param obj the object
   * @param field the dot path
   * @return the value, {@link Undefined#INSTANCE} when absent
   */
  public static Object getDotValue(Object obj, String field) {
    return getDotValue(obj, List.of(field.split("\\.", -1)));
  }

  /**
   * {@link #getDotValue(Object, String)} with the path already split.
   *
   * @param obj the object
   * @param fieldParts the path segments
   * @return the value, {@link Undefined#INSTANCE} when absent
   */
  public static Object getDotValue(Object obj, List<String> fieldParts) {
    if (!Js.truthy(obj)) {
      return UNDEFINED;
    }
    if (fieldParts.isEmpty()) {
      return obj;
    }
    if (fieldParts.size() == 1) {
      return Js.get(obj, fieldParts.get(0));
    }
    Object first = Js.get(obj, fieldParts.get(0));
    if (first instanceof List<?> list) {
      double index = Js.parseInt(fieldParts.get(1));
      if (!Double.isNaN(index)) {
        Object element = index >= 0 && index < list.size() ? list.get((int) index) : UNDEFINED;
        return getDotValue(element, fieldParts.subList(2, fieldParts.size()));
      }
      List<Object> values = new ArrayList<>(list.size());
      for (Object element : list) {
        values.add(getDotValue(element, fieldParts.subList(1, fieldParts.size())));
      }
      return values;
    }
    return getDotValue(first, fieldParts.subList(1, fieldParts.size()));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Equality and ordering
  // ---------------------------------------------------------------------------------------------------------------

  private static boolean isDate(Object value) {
    return value instanceof NedbDate;
  }

  /**
   * @param value a value
   * @return whether it is a regular expression ({@link JsRegExp} or {@link Pattern})
   */
  public static boolean isRegExp(Object value) {
    return value instanceof JsRegExp || value instanceof Pattern;
  }

  private static boolean testRegExp(Object regExp, String value) {
    if (regExp instanceof JsRegExp jsRegExp) {
      return jsRegExp.test(value);
    }
    return JsRegExp.find((Pattern) regExp, value);
  }

  /**
   * {@code isPrimitiveType(obj)}: booleans, numbers, strings, {@code null}, dates and arrays.
   *
   * @param obj a value
   * @return whether NeDB treats it as a primitive in {@link #match}
   */
  public static boolean isPrimitiveType(Object obj) {
    return obj == null || obj instanceof Boolean || obj instanceof Number || obj instanceof String || isDate(obj)
        || obj instanceof List<?>;
  }

  /** {@code typeof v === 'object' && v !== null}. */
  private static boolean isObject(Object value) {
    return value != null && "object".equals(Js.typeOf(value));
  }

  /** JavaScript {@code a === b} for raw values. */
  static boolean strictEquals(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y) {
      return x.doubleValue() == y.doubleValue();
    }
    if (a instanceof String || a instanceof Boolean) {
      return a.equals(b);
    }
    return a == b;
  }

  /**
   * {@code areThingsEqual(a, b)}: primitives with {@code ===}, dates by time, arrays and objects deeply (same key
   * count, every key of {@code a} present in {@code b} with an equal value); an array never equals a non-array and
   * undefined never equals anything.
   *
   * @param a a value
   * @param b a value
   * @return whether NeDB considers them equal
   */
  public static boolean areThingsEqual(Object a, Object b) {
    if (a == null || a instanceof String || a instanceof Boolean || a instanceof Number
        || b == null || b instanceof String || b instanceof Boolean || b instanceof Number) {
      return strictEquals(a, b);
    }
    if (isDate(a) || isDate(b)) {
      return isDate(a) && isDate(b) && ((NedbDate) a).epochMillis() == ((NedbDate) b).epochMillis();
    }
    boolean aArray = a instanceof List<?>;
    boolean bArray = b instanceof List<?>;
    if ((!(aArray && bArray) && (aArray || bArray)) || a == UNDEFINED || b == UNDEFINED) {
      return false;
    }
    List<String> aKeys = Js.keys(a);
    List<String> bKeys = Js.keys(b);
    if (aKeys.size() != bKeys.size()) {
      return false;
    }
    for (String key : aKeys) {
      if (!bKeys.contains(key)) {
        return false;
      }
      if (!areThingsEqual(Js.get(a, key), Js.get(b, key))) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@code areComparable(a, b)}: comparison operators only apply between two strings, two numbers or two dates (more
   * precisely, when one side is a string, number or date and both have the same {@code typeof}).
   *
   * @param a a value
   * @param b a value
   * @return whether {@code $lt}/{@code $lte}/{@code $gt}/{@code $gte} may compare them
   */
  public static boolean areComparable(Object a, Object b) {
    if (!(a instanceof String) && !(a instanceof Number) && !isDate(a)
        && !(b instanceof String) && !(b instanceof Number) && !isDate(b)) {
      return false;
    }
    return Js.typeOf(a).equals(Js.typeOf(b));
  }

  private static Object toPrimitive(Object value) {
    if (value instanceof NedbDate date) {
      return date.epochMillis();
    }
    if (value instanceof List<?> || value instanceof Map<?, ?> || value instanceof JsRegExp || value instanceof Pattern) {
      return Js.toJsString(value);
    }
    return value;
  }

  /** JavaScript's relational operators ({@code <}, {@code <=}, {@code >}, {@code >=}) with their coercions. */
  static boolean relational(Object a, Object b, String operator) {
    Object x = toPrimitive(a);
    Object y = toPrimitive(b);
    if (x instanceof String sx && y instanceof String sy) {
      int comparison = sx.compareTo(sy);
      return switch (operator) {
        case "<" -> comparison < 0;
        case "<=" -> comparison <= 0;
        case ">" -> comparison > 0;
        default -> comparison >= 0;
      };
    }
    double nx = Js.toNumber(x);
    double ny = Js.toNumber(y);
    return switch (operator) {
      case "<" -> nx < ny;
      case "<=" -> nx <= ny;
      case ">" -> nx > ny;
      default -> nx >= ny;
    };
  }

  private static int compareNsb(double a, double b) {
    if (a < b) {
      return -1;
    }
    if (a > b) {
      return 1;
    }
    return 0;
  }

  private static int compareArrays(List<?> a, List<?> b) {
    int minLength = Math.min(a.size(), b.size());
    for (int i = 0; i < minLength; i++) {
      int comparison = compareThings(a.get(i), b.get(i));
      if (comparison != 0) {
        return comparison;
      }
    }
    return compareNsb(a.size(), b.size());
  }

  /**
   * {@code compareThings(a, b)}: NeDB's total order across types,
   * {@code undefined < null < numbers < strings < booleans < dates < arrays < objects}; numbers numerically, strings
   * by UTF-16 code units, {@code false < true}, arrays element by element (then by length), objects by the values of
   * their sorted keys (then by key count).
   *
   * @param a a value
   * @param b a value
   * @return negative, zero or positive
   */
  public static int compareThings(Object a, Object b) {
    if (a == UNDEFINED) {
      return b == UNDEFINED ? 0 : -1;
    }
    if (b == UNDEFINED) {
      return 1;
    }
    if (a == null) {
      return b == null ? 0 : -1;
    }
    if (b == null) {
      return 1;
    }
    if (a instanceof Number x) {
      return b instanceof Number y ? compareNsb(x.doubleValue(), y.doubleValue()) : -1;
    }
    if (b instanceof Number) {
      return 1;
    }
    if (a instanceof String x) {
      return b instanceof String y ? Integer.signum(x.compareTo(y)) : -1;
    }
    if (b instanceof String) {
      return 1;
    }
    if (a instanceof Boolean x) {
      return b instanceof Boolean y ? Boolean.compare(x, y) : -1;
    }
    if (b instanceof Boolean) {
      return 1;
    }
    if (a instanceof NedbDate x) {
      return b instanceof NedbDate y ? compareNsb(x.epochMillis(), y.epochMillis()) : -1;
    }
    if (b instanceof NedbDate) {
      return 1;
    }
    if (a instanceof List<?> x) {
      return b instanceof List<?> y ? compareArrays(x, y) : -1;
    }
    if (b instanceof List<?>) {
      return 1;
    }
    List<String> aKeys = new ArrayList<>(Js.keys(a));
    List<String> bKeys = new ArrayList<>(Js.keys(b));
    Collections.sort(aKeys);
    Collections.sort(bKeys);
    for (int i = 0; i < Math.min(aKeys.size(), bKeys.size()); i++) {
      int comparison = compareThings(Js.get(a, aKeys.get(i)), Js.get(b, bKeys.get(i)));
      if (comparison != 0) {
        return comparison;
      }
    }
    return compareNsb(aKeys.size(), bKeys.size());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Matching
  // ---------------------------------------------------------------------------------------------------------------

  private static boolean comparison(String operator, Object a, Object b) {
    switch (operator) {
      case "$lt":
        return areComparable(a, b) && relational(a, b, "<");
      case "$lte":
        return areComparable(a, b) && relational(a, b, "<=");
      case "$gt":
        return areComparable(a, b) && relational(a, b, ">");
      case "$gte":
        return areComparable(a, b) && relational(a, b, ">=");
      case "$ne":
        return a == UNDEFINED || !areThingsEqual(a, b);
      case "$in":
        return in(a, b);
      case "$nin":
        if (!(b instanceof List<?>)) {
          throw new NedbError("$nin operator called with a non-array");
        }
        return !in(a, b);
      case "$regex":
        if (!isRegExp(b)) {
          throw new NedbError("$regex operator called with non regular expression");
        }
        return a instanceof String s && testRegExp(b, s);
      case "$exists":
        boolean exists = Js.truthy(b) || "".equals(b);
        return a == UNDEFINED ? !exists : exists;
      case "$size":
        if (!(a instanceof List<?> list)) {
          return false;
        }
        double remainder = Js.toNumber(b) % 1;
        if (remainder != 0) {
          throw new NedbError("$size operator called without an integer");
        }
        return b instanceof Number size && list.size() == size.doubleValue();
      case "$elemMatch":
        if (!(a instanceof List<?> elements)) {
          return false;
        }
        for (Object element : elements) {
          if (match(element, b)) {
            return true;
          }
        }
        return false;
      default:
        throw new NedbError("Unknown comparison function " + operator);
    }
  }

  private static boolean isComparisonOperator(String key) {
    return switch (key) {
      case "$lt", "$lte", "$gt", "$gte", "$ne", "$in", "$nin", "$regex", "$exists", "$size", "$elemMatch" -> true;
      default -> false;
    };
  }

  private static boolean in(Object a, Object b) {
    if (!(b instanceof List<?> list)) {
      throw new NedbError("$in operator called with a non-array");
    }
    for (Object element : list) {
      if (areThingsEqual(a, element)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static boolean logical(String operator, Object obj, Object query) {
    switch (operator) {
      case "$or" -> {
        if (!(query instanceof List<?> queries)) {
          throw new NedbError("$or operator used without an array");
        }
        for (Object subQuery : queries) {
          if (match(obj, subQuery)) {
            return true;
          }
        }
        return false;
      }
      case "$and" -> {
        if (!(query instanceof List<?> queries)) {
          throw new NedbError("$and operator used without an array");
        }
        for (Object subQuery : queries) {
          if (!match(obj, subQuery)) {
            return false;
          }
        }
        return true;
      }
      case "$not" -> {
        return !match(obj, query);
      }
      case "$where" -> {
        if (!(query instanceof Predicate<?> predicate)) {
          throw new NedbError("$where operator used without a function");
        }
        return ((Predicate<Object>) predicate).test(obj);
      }
      default -> throw new NedbError("Unknown logical operator " + operator);
    }
  }

  /**
   * {@code match(obj, query)}: whether a document matches a query. All top-level keys must match (implicit AND);
   * {@code $and}/{@code $or}/{@code $not}/{@code $where} are logical operators; other keys are dot paths matched with
   * {@link #matchQueryPart}. A primitive document or query (as found inside {@code $elemMatch}) is compared as a value.
   *
   * @param obj the document (or value)
   * @param query the query
   * @return whether it matches
   * @throws NedbError for malformed queries (unknown operators, mixed operators and fields, ...)
   */
  public static boolean match(Object obj, Object query) {
    if (isPrimitiveType(obj) || isPrimitiveType(query)) {
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put("needAKey", obj);
      return matchQueryPart(wrapper, "needAKey", query, false);
    }
    if (query instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String queryKey = String.valueOf(entry.getKey());
        Object queryValue = entry.getValue();
        if (queryKey.startsWith("$")) {
          if (!logical(queryKey, obj, queryValue)) {
            return false;
          }
        } else if (!matchQueryPart(obj, queryKey, queryValue, false)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean matchQueryPart(Object obj, String queryKey, Object queryValue, boolean treatObjAsValue) {
    Object objValue = getDotValue(obj, queryKey);

    if (objValue instanceof List<?> elements && !treatObjAsValue) {
      if (queryValue instanceof List<?>) {
        return matchQueryPart(obj, queryKey, queryValue, true);
      }
      if (isObject(queryValue) && !isRegExp(queryValue)) {
        for (String key : Js.keys(queryValue)) {
          if (key.equals("$size") || key.equals("$elemMatch")) {
            return matchQueryPart(obj, queryKey, queryValue, true);
          }
        }
      }
      for (Object element : elements) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("k", element);
        if (matchQueryPart(wrapper, "k", queryValue, false)) {
          return true;
        }
      }
      return false;
    }

    if (isObject(queryValue) && !isRegExp(queryValue) && !(queryValue instanceof List<?>)) {
      List<String> keys = Js.keys(queryValue);
      int dollarKeys = 0;
      for (String key : keys) {
        if (key.startsWith("$")) {
          dollarKeys++;
        }
      }
      if (dollarKeys != 0 && dollarKeys != keys.size()) {
        throw new NedbError("You cannot mix operators and normal fields");
      }
      if (dollarKeys > 0) {
        for (String key : keys) {
          if (!isComparisonOperator(key)) {
            throw new NedbError("Unknown comparison function " + key);
          }
          if (!comparison(key, objValue, Js.get(queryValue, key))) {
            return false;
          }
        }
        return true;
      }
    }

    if (isRegExp(queryValue)) {
      return comparison("$regex", objValue, queryValue);
    }

    return areThingsEqual(objValue, queryValue);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Modifiers
  // ---------------------------------------------------------------------------------------------------------------

  private static boolean isModifier(String name) {
    return switch (name) {
      case "$set", "$unset", "$inc", "$min", "$max", "$push", "$addToSet", "$pop", "$pull" -> true;
      default -> false;
    };
  }

  /**
   * {@code modify(obj, updateQuery)}: the new version of a document. An update without {@code $} keys replaces the
   * whole document (keeping {@code _id}); otherwise the modifiers are applied to a copy.
   *
   * @param obj the current document
   * @param updateQuery the replacement or the modifiers
   * @return the new document
   * @throws NedbError {@code You cannot change a document's _id}, {@code You cannot mix modifiers and normal fields},
   *     {@code Unknown modifier $x}, invalid field names, and the modifiers' own errors
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> modify(Map<String, Object> obj, Map<String, Object> updateQuery) {
    List<String> keys = new ArrayList<>(updateQuery.keySet());
    int dollarKeys = 0;
    for (String key : keys) {
      if (key.startsWith("$")) {
        dollarKeys++;
      }
    }
    if (keys.contains("_id") && !strictEquals(updateQuery.get("_id"), Js.get(obj, "_id"))) {
      throw new NedbError("You cannot change a document's _id");
    }
    if (dollarKeys != 0 && dollarKeys != keys.size()) {
      throw new NedbError("You cannot mix modifiers and normal fields");
    }
    Map<String, Object> newDoc;
    if (dollarKeys == 0) {
      newDoc = copyDocument(updateQuery);
      Object id = Js.get(obj, "_id");
      if (id == UNDEFINED) {
        newDoc.remove("_id");
      } else {
        newDoc.put("_id", id);
      }
    } else {
      newDoc = copyDocument(obj);
      for (String modifier : new LinkedHashSet<>(keys)) {
        if (!isModifier(modifier)) {
          throw new NedbError("Unknown modifier " + modifier);
        }
        Object argument = updateQuery.get(modifier);
        if (!isObject(argument) && argument != null) {
          throw new NedbError("Modifier " + modifier + "'s argument must be an object");
        }
        for (String field : Js.keys(argument)) {
          applyModifier(modifier, newDoc, field, Js.get(argument, field));
        }
      }
    }
    checkObject(newDoc);
    if (!strictEquals(Js.get(obj, "_id"), Js.get(newDoc, "_id"))) {
      throw new NedbError("You can't change a document's _id");
    }
    return newDoc;
  }

  private static void applyModifier(String modifier, Object obj, String field, Object value) {
    applyModifier(modifier, obj, List.of(field.split("\\.", -1)), value);
  }

  private static void applyModifier(String modifier, Object obj, List<String> fieldParts, Object value) {
    if (fieldParts.size() == 1) {
      lastStep(modifier, obj, fieldParts.get(0), value);
      return;
    }
    Object next = read(obj, fieldParts.get(0));
    if (next == UNDEFINED) {
      if (modifier.equals("$unset")) {
        return;
      }
      set(obj, fieldParts.get(0), new LinkedHashMap<String, Object>());
      // NeDB reads the property again: on a primitive the assignment was ignored and this is undefined
      next = read(obj, fieldParts.get(0));
    }
    applyModifier(modifier, next, fieldParts.subList(1, fieldParts.size()), value);
  }

  /** {@code obj[field]} in NeDB's (sloppy-mode) modifier code: reading a property of null or undefined throws. */
  private static Object read(Object obj, String field) {
    if (obj == null || obj == UNDEFINED) {
      throw new NedbError("Cannot read properties of " + Js.toJsString(obj) + " (reading '" + field + "')");
    }
    return Js.get(obj, field);
  }

  /**
   * {@code obj[field] = value} in NeDB's (sloppy-mode) modifier code: maps get the entry, arrays the element; setting a
   * property of a primitive (or a date) is silently ignored, as is a non-index property of an array (JavaScript keeps
   * it on the array object, but serialization drops it); null and undefined throw.
   */
  @SuppressWarnings("unchecked")
  private static void set(Object obj, String field, Object value) {
    if (obj instanceof Map<?, ?> map) {
      ((Map<String, Object>) map).put(field, value);
      return;
    }
    if (obj instanceof List<?> list) {
      int index = Js.arrayIndex(field);
      if (index >= 0) {
        List<Object> elements = (List<Object>) list;
        while (elements.size() <= index) {
          elements.add(null);
        }
        elements.set(index, value);
      }
      return;
    }
    if (obj == null || obj == UNDEFINED) {
      throw new NedbError("Cannot set properties of " + Js.toJsString(obj) + " (setting '" + field + "')");
    }
  }

  /** {@code Object.prototype.hasOwnProperty.call(obj, field)}. */
  private static boolean hasOwnProperty(Object obj, String field) {
    if (obj == null || obj == UNDEFINED) {
      throw new NedbError("Cannot convert undefined or null to object");
    }
    return Js.get(obj, field) != UNDEFINED || (obj instanceof Map<?, ?> map && map.containsKey(field));
  }

  @SuppressWarnings("unchecked")
  private static void lastStep(String modifier, Object obj, String field, Object value) {
    switch (modifier) {
      case "$set" -> set(obj, field, deepCopy(value));
      case "$unset" -> {
        if (obj == null || obj == UNDEFINED) {
          throw new NedbError("Cannot convert undefined or null to object");
        }
        if (obj instanceof Map<?, ?> map) {
          map.remove(field);
        } else if (obj instanceof List<?> list && Js.arrayIndex(field) >= 0 && Js.arrayIndex(field) < list.size()) {
          ((List<Object>) list).set(Js.arrayIndex(field), null);
        }
      }
      case "$push" -> push(obj, field, value);
      case "$addToSet" -> addToSet(obj, field, value);
      case "$pop" -> {
        if (!(read(obj, field) instanceof List<?> list)) {
          throw new NedbError("Can't $pop an element from non-array values");
        }
        if (!(value instanceof Number number)) {
          throw new NedbError(Js.toJsString(value) + " isn't an integer, can't use it with $pop");
        }
        if (number.doubleValue() == 0) {
          return;
        }
        set(obj, field, number.doubleValue() > 0
            ? new ArrayList<>(list.subList(0, Math.max(list.size() - 1, 0)))
            : new ArrayList<>(list.subList(Math.min(1, list.size()), list.size())));
      }
      case "$pull" -> {
        if (!(read(obj, field) instanceof List<?> list)) {
          throw new NedbError("Can't $pull an element from non-array values");
        }
        List<Object> elements = (List<Object>) list;
        for (int i = elements.size() - 1; i >= 0; i--) {
          if (match(elements.get(i), value)) {
            elements.remove(i);
          }
        }
      }
      case "$inc" -> {
        if (!(value instanceof Number increment)) {
          throw new NedbError(Js.toJsString(value) + " must be a number");
        }
        Object current = read(obj, field);
        if (!(current instanceof Number number)) {
          if (!hasOwnProperty(obj, field)) {
            set(obj, field, value);
          } else {
            throw new NedbError("Don't use the $inc modifier on non-number fields");
          }
        } else {
          set(obj, field, add(number, increment));
        }
      }
      case "$max" -> {
        Object current = read(obj, field);
        if (current == UNDEFINED || relational(value, current, ">")) {
          set(obj, field, deepCopy(value));
        }
      }
      case "$min" -> {
        Object current = read(obj, field);
        if (current == UNDEFINED || relational(value, current, "<")) {
          set(obj, field, deepCopy(value));
        }
      }
      default -> throw new NedbError("Unknown modifier " + modifier);
    }
  }

  private static Number add(Number a, Number b) {
    if (isIntegral(a) && isIntegral(b)) {
      try {
        return Math.addExact(a.longValue(), b.longValue());
      } catch (ArithmeticException overflow) {
        return a.doubleValue() + b.doubleValue();
      }
    }
    return a.doubleValue() + b.doubleValue();
  }

  private static boolean isIntegral(Number number) {
    return number instanceof Integer || number instanceof Long || number instanceof Short || number instanceof Byte;
  }

  @SuppressWarnings("unchecked")
  private static void push(Object obj, String field, Object value) {
    if (!hasOwnProperty(obj, field)) {
      set(obj, field, new ArrayList<>());
    }
    if (!(read(obj, field) instanceof List<?> target)) {
      throw new NedbError("Can't $push an element on non-array values");
    }
    List<Object> elements = (List<Object>) target;
    boolean addedEach = value instanceof Map<?, ?> m && !m.containsKey("$each") && Js.truthy(m.get("$slice"));
    if (value instanceof Map<?, ?> map && (addedEach || Js.truthy(map.get("$each")))) {
      Object each = addedEach ? List.of() : map.get("$each");
      int size = map.size() + (addedEach ? 1 : 0);
      if (size >= 3 || (size == 2 && !map.containsKey("$slice"))) {
        throw new NedbError("Can only use $slice in cunjunction with $each when $push to array");
      }
      if (!(each instanceof List<?> eachList)) {
        throw new NedbError("$each requires an array value");
      }
      for (Object element : eachList) {
        elements.add(deepCopy(element));
      }
      if (!(map.get("$slice") instanceof Number slice)) {
        return;
      }
      double sliceValue = slice.doubleValue();
      int n = elements.size();
      if (sliceValue == 0) {
        set(obj, field, new ArrayList<>());
      } else if (sliceValue < 0) {
        set(obj, field, Js.slice(elements, Math.max(0, n + sliceValue), n));
      } else {
        set(obj, field, Js.slice(elements, 0, Math.min(n, sliceValue)));
      }
    } else {
      elements.add(deepCopy(value));
    }
  }

  @SuppressWarnings("unchecked")
  private static void addToSet(Object obj, String field, Object value) {
    if (!hasOwnProperty(obj, field)) {
      set(obj, field, new ArrayList<>());
    }
    if (!(read(obj, field) instanceof List<?> target)) {
      throw new NedbError("Can't $addToSet an element on non-array values");
    }
    if (value instanceof Map<?, ?> map && Js.truthy(map.get("$each"))) {
      if (map.size() > 1) {
        throw new NedbError("Can't use another field in conjunction with $each");
      }
      if (!(map.get("$each") instanceof List<?> each)) {
        throw new NedbError("$each requires an array value");
      }
      for (Object element : each) {
        addToSet(obj, field, element);
      }
      return;
    }
    for (Object element : target) {
      if (compareThings(element, value) == 0) {
        return;
      }
    }
    ((List<Object>) target).add(deepCopy(value));
  }
}
