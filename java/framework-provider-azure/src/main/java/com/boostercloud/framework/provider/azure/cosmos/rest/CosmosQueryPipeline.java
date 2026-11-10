package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.types.BoosterJson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The client-side half of a cross-partition query, as the SDKs implement it: merges the results each partition key
 * range returned for the plan's rewritten query, then applies, in this order, {@code ORDER BY} (merge on
 * {@code orderByItems}, ties keep the range order), {@code VALUE} aggregates, {@code DISTINCT}, {@code OFFSET/LIMIT}
 * and {@code TOP}.
 */
final class CosmosQueryPipeline {

  private CosmosQueryPipeline() {}

  static List<Object> combine(CosmosQueryPlan plan, List<List<Object>> perRange) {
    List<Object> results;
    if (!plan.orderBy().isEmpty()) {
      results = orderBy(plan, perRange);
    } else if (!plan.aggregates().isEmpty()) {
      results = aggregate(plan, perRange);
    } else {
      results = new ArrayList<>();
      perRange.forEach(results::addAll);
    }
    results = distinct(plan.distinctType(), results);
    if (plan.offset() != null || plan.limit() != null) {
      int offset = plan.offset() == null ? 0 : Math.max(0, plan.offset());
      int from = Math.min(offset, results.size());
      int to = plan.limit() == null ? results.size() : Math.min(results.size(), from + Math.max(0, plan.limit()));
      results = new ArrayList<>(results.subList(from, to));
    }
    if (plan.top() != null) {
      results = new ArrayList<>(results.subList(0, Math.min(results.size(), Math.max(0, plan.top()))));
    }
    return results;
  }

  private static List<Object> orderBy(CosmosQueryPlan plan, List<List<Object>> perRange) {
    List<Object> rows = new ArrayList<>();
    perRange.forEach(rows::addAll);
    Comparator<Object> comparator = (left, right) -> {
      List<Object> leftItems = orderByItems(left);
      List<Object> rightItems = orderByItems(right);
      for (int i = 0; i < plan.orderBy().size(); i++) {
        int result = CosmosValueComparator.INSTANCE.compare(item(leftItems, i), item(rightItems, i));
        if (result != 0) {
          return "Descending".equals(plan.orderBy().get(i)) ? -result : result;
        }
      }
      return 0;
    };
    // List.sort is stable: equal rows keep the range order, as the SDK's k-way merge does.
    rows.sort(comparator);
    List<Object> payloads = new ArrayList<>();
    for (Object row : rows) {
      payloads.add(row instanceof Map<?, ?> map && map.containsKey("payload") ? map.get("payload") : row);
    }
    return payloads;
  }

  private static List<Object> orderByItems(Object row) {
    if (row instanceof Map<?, ?> map && map.get("orderByItems") instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    return List.of();
  }

  private static Object item(List<Object> items, int index) {
    if (index >= items.size() || !(items.get(index) instanceof Map<?, ?> wrapper) || !wrapper.containsKey("item")) {
      return CosmosValueComparator.UNDEFINED;
    }
    return wrapper.get("item");
  }

  private static List<Object> aggregate(CosmosQueryPlan plan, List<List<Object>> perRange) {
    int count = plan.aggregates().size();
    List<Object> values = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      values.add(CosmosValueComparator.UNDEFINED);
    }
    for (List<Object> rangeResults : perRange) {
      for (Object row : rangeResults) {
        List<?> items = row instanceof List<?> list ? list : List.of(row);
        for (int i = 0; i < Math.min(count, items.size()); i++) {
          Object partial = items.get(i) instanceof Map<?, ?> wrapper && wrapper.containsKey("item")
              ? wrapper.get("item")
              : (items.get(i) instanceof Map<?, ?> ? CosmosValueComparator.UNDEFINED : items.get(i));
          values.set(i, accumulate(plan.aggregates().get(i), values.get(i), partial));
        }
      }
    }
    List<Object> finals = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      finals.add(finish(plan.aggregates().get(i), values.get(i)));
    }
    Object value = finals.getFirst();
    return value == CosmosValueComparator.UNDEFINED ? new ArrayList<>() : new ArrayList<>(List.of(value));
  }

  private static Object accumulate(String aggregate, Object current, Object partial) {
    if (partial == CosmosValueComparator.UNDEFINED) {
      return current;
    }
    switch (aggregate) {
      case "Count", "Sum" -> {
        if (!(partial instanceof Number number)) {
          return current;
        }
        double base = current instanceof Number n ? n.doubleValue() : 0;
        return base + number.doubleValue();
      }
      case "Min", "Max" -> {
        Object candidate = partial;
        if (partial instanceof Map<?, ?> map) {
          candidate = map.containsKey("min") ? map.get("min") : map.containsKey("max") ? map.get("max")
              : CosmosValueComparator.UNDEFINED;
        }
        if (candidate == CosmosValueComparator.UNDEFINED) {
          return current;
        }
        if (current == CosmosValueComparator.UNDEFINED) {
          return candidate;
        }
        int comparison = CosmosValueComparator.INSTANCE.compare(candidate, current);
        return aggregate.equals("Min") ? (comparison < 0 ? candidate : current) : (comparison > 0 ? candidate : current);
      }
      case "Average" -> {
        if (!(partial instanceof Map<?, ?> map) || !(map.get("sum") instanceof Number sum)
            || !(map.get("count") instanceof Number partialCount)) {
          return current;
        }
        double[] acc = current instanceof double[] a ? a : new double[] {0, 0};
        return new double[] {acc[0] + sum.doubleValue(), acc[1] + partialCount.doubleValue()};
      }
      default -> throw new UnsupportedOperationException("Unsupported aggregate " + aggregate);
    }
  }

  private static Object finish(String aggregate, Object value) {
    if (value == CosmosValueComparator.UNDEFINED) {
      return "Count".equals(aggregate) ? 0 : CosmosValueComparator.UNDEFINED;
    }
    if (value instanceof double[] average) {
      return average[1] == 0 ? CosmosValueComparator.UNDEFINED : normalise(average[0] / average[1]);
    }
    return value instanceof Double d ? normalise(d) : value;
  }

  private static Object normalise(double value) {
    if (value == Math.rint(value) && Math.abs(value) < Integer.MAX_VALUE) {
      return (int) value;
    }
    if (value == Math.rint(value) && Math.abs(value) < Long.MAX_VALUE) {
      return (long) value;
    }
    return value;
  }

  private static List<Object> distinct(String distinctType, List<Object> rows) {
    if (distinctType == null || distinctType.equals("None")) {
      return rows;
    }
    List<Object> result = new ArrayList<>();
    if (distinctType.equals("Ordered")) {
      String previous = null;
      for (Object row : rows) {
        String key = BoosterJson.toJson(row);
        if (!Objects.equals(key, previous)) {
          result.add(row);
        }
        previous = key;
      }
      return result;
    }
    Set<String> seen = new HashSet<>();
    for (Object row : rows) {
      if (seen.add(BoosterJson.toJson(row))) {
        result.add(row);
      }
    }
    return result;
  }
}
