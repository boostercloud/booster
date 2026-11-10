package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.provider.local.store.JsRegExp;
import com.boostercloud.framework.provider.local.store.Js;
import com.boostercloud.framework.types.BoosterJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates a Booster filter ({@code FilterFor} JSON shape) into a NeDB query on read model envelopes. Port of
 * {@code library/searcher-adapter.ts}.
 *
 * <table>
 *   <caption>Operators</caption>
 *   <tr><th>Booster</th><th>NeDB</th></tr>
 *   <tr><td>{@code { p: { eq: v } }}</td><td>{@code { 'value.p': v }}</td></tr>
 *   <tr><td>{@code ne}, {@code lt}, {@code gt}, {@code lte}, {@code gte}</td><td>{@code $ne}, {@code $lt}, {@code $gt},
 *       {@code $lte}, {@code $gte}</td></tr>
 *   <tr><td>{@code in: [a, b]}</td><td>{@code $in: [a, b]} (a single value is wrapped)</td></tr>
 *   <tr><td>{@code isDefined: b}</td><td>{@code $exists: b}</td></tr>
 *   <tr><td>{@code contains: s}, {@code regex: s}</td><td>{@code $regex: new RegExp(s)}</td></tr>
 *   <tr><td>{@code beginsWith: s}</td><td>{@code $regex: new RegExp('^' + s)}</td></tr>
 *   <tr><td>{@code iRegex: s}</td><td>{@code $regex: new RegExp(s, 'i')}</td></tr>
 *   <tr><td>{@code includes: 'str'}</td><td>{@code $regex: new RegExp('str')}</td></tr>
 *   <tr><td>{@code includes: other}</td><td>{@code $elemMatch: other}</td></tr>
 *   <tr><td>{@code { a: { b: { eq: v } } }}</td><td>{@code { 'value.a.b': v }}</td></tr>
 *   <tr><td>{@code and} / {@code or} / {@code not}</td><td>{@code $and} / {@code $or} / {@code $not}</td></tr>
 * </table>
 *
 * <p>TypeScript behaviours kept on purpose: an object is an operator object when its <em>first</em> key is an
 * operator, and then only its first operator is translated ({@code { gte: 1, lte: 10 }} gives {@code { $gte: 1 }});
 * array operands of single-value operators use their first element; regular-expression sources are not escaped;
 * operators that need a string throw {@code Attempted to perform a <operation> operation on a non-string}. Operand
 * values that are not raw JSON (a {@code UUID}, an enum, a date) are converted to their JSON form first.
 */
public final class SearcherAdapter {

  private static final Set<String> QUERY_OPERATORS = Set.of(
      "eq", "ne", "lt", "gt", "lte", "gte", "in", "isDefined", "contains", "beginsWith", "includes", "regex", "iRegex");

  private SearcherAdapter() {}

  /**
   * {@code queryRecordFor(filters)}.
   *
   * @param filters the Booster filter
   * @return the NeDB query
   */
  public static Map<String, Object> queryRecordFor(Map<String, Object> filters) {
    return queryRecordFor(filters, null, new LinkedHashMap<>());
  }

  /**
   * {@code queryRecordFor(filters, nested?, queryFromFilters = {})}.
   *
   * @param filters the (sub-)filter
   * @param nested the dot path of the enclosing property, or {@code null}
   * @param queryFromFilters the query being built
   * @return a shallow copy of the query
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> queryRecordFor(Object filters, String nested, Map<String, Object> queryFromFilters) {
    if (filters == null || filters == com.boostercloud.framework.provider.local.store.Undefined.INSTANCE) {
      throw new IllegalArgumentException("Cannot convert undefined or null to object");
    }
    List<String> keys = Js.keys(filters);
    if (!keys.isEmpty()) {
      for (String key : keys) {
        String propName = nested != null && !nested.isEmpty() ? nested + "." + key : key;
        Object filter = Js.get(filters, key);
        switch (key) {
          case "not" -> queryFromFilters.put("$" + propName, queryRecordFor(filter, null, new LinkedHashMap<>()));
          case "or", "and" -> {
            if (!(filter instanceof List<?> subFilters)) {
              throw new IllegalArgumentException("filters[key].map is not a function");
            }
            List<Object> translated = new ArrayList<>();
            for (Object subFilter : subFilters) {
              translated.add(queryRecordFor(subFilter, null, new LinkedHashMap<>()));
            }
            queryFromFilters.put("$" + propName, translated);
          }
          default -> {
            if (filter == null) {
              throw new IllegalArgumentException("Cannot convert undefined or null to object");
            }
            List<String> filterKeys = Js.keys(filter);
            if (filterKeys.isEmpty() || !QUERY_OPERATORS.contains(filterKeys.get(0))) {
              if (filter instanceof String string && !string.isEmpty()) {
                throw new IllegalArgumentException("Invalid filter for property '" + propName
                    + "': expected an operator object but got a string (TypeScript recurses forever here)");
              }
              queryRecordFor(filter, propName, queryFromFilters);
            } else {
              queryFromFilters.put("value." + propName, filterToQuery((Map<String, Object>) filter));
            }
          }
        }
      }
    }
    return new LinkedHashMap<>(queryFromFilters);
  }

  /** {@code filterToQuery(filter)}: translates every entry but returns only the first one. */
  private static Object filterToQuery(Map<String, Object> filter) {
    Object first = null;
    boolean isFirst = true;
    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      String operation = entry.getKey();
      if (!QUERY_OPERATORS.contains(operation)) {
        throw new IllegalArgumentException("query is not a function");
      }
      Object operand = entry.getValue();
      List<Object> values = new ArrayList<>();
      if (operand instanceof List<?> list) {
        for (Object element : list) {
          values.add(toRaw(element));
        }
      } else {
        values.add(toRaw(operand));
      }
      Object query = translate(operation, values);
      if (isFirst) {
        first = query;
        isFirst = false;
      }
    }
    return first;
  }

  private static Object toRaw(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number
        || value instanceof Map<?, ?> || value instanceof List<?>) {
      return value instanceof Map<?, ?> || value instanceof List<?> ? BoosterJson.toRaw(value) : value;
    }
    return BoosterJson.toRaw(value);
  }

  private static Object first(List<Object> values) {
    return values.isEmpty() ? com.boostercloud.framework.provider.local.store.Undefined.INSTANCE : values.get(0);
  }

  private static Map<String, Object> operator(String name, Object value) {
    Map<String, Object> query = new LinkedHashMap<>();
    query.put(name, value);
    return query;
  }

  /** {@code queryOperatorTable[operation](values)}. */
  private static Object translate(String operation, List<Object> values) {
    return switch (operation) {
      case "eq" -> first(values);
      case "ne" -> operator("$ne", first(values));
      case "lt" -> operator("$lt", first(values));
      case "gt" -> operator("$gt", first(values));
      case "lte" -> operator("$lte", first(values));
      case "gte" -> operator("$gte", first(values));
      case "in" -> operator("$in", values);
      case "isDefined" -> operator("$exists", first(values));
      case "contains" -> buildRegexQuery("contains", values);
      case "beginsWith" -> buildRegexQuery("begins-with", values);
      case "includes" -> buildIncludes(values);
      case "regex" -> buildRegexQuery("regex", values);
      case "iRegex" -> buildRegexQuery("iRegex", values);
      default -> throw new IllegalArgumentException("query is not a function");
    };
  }

  private static Object buildIncludes(List<Object> values) {
    Object matcher = first(values);
    if (matcher instanceof String string) {
      return operator("$regex", new JsRegExp(string));
    }
    return operator("$elemMatch", matcher);
  }

  private static Object buildRegexQuery(String operation, List<Object> values) {
    Object matcher = first(values);
    if (!(matcher instanceof String string)) {
      throw new IllegalArgumentException("Attempted to perform a " + operation + " operation on a non-string");
    }
    if (operation.equals("begins-with")) {
      return operator("$regex", new JsRegExp("^" + string));
    }
    if (operation.equals("iRegex")) {
      return operator("$regex", new JsRegExp(string, "i"));
    }
    return operator("$regex", new JsRegExp(string));
  }
}
