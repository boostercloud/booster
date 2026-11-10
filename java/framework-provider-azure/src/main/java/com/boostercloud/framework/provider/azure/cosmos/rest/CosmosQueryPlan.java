package com.boostercloud.framework.provider.azure.cosmos.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The "partitioned query execution info" returned by the gateway for a query-plan request
 * ({@code x-ms-cosmos-is-query-plan-request: True}): what the client must do to run a cross-partition query.
 *
 * @param distinctType {@code None}, {@code Ordered} or {@code Unordered}
 * @param top the {@code TOP} value, or {@code null}
 * @param offset the {@code OFFSET} value, or {@code null}
 * @param limit the {@code LIMIT} value, or {@code null}
 * @param orderBy {@code Ascending}/{@code Descending} per {@code ORDER BY} expression
 * @param aggregates {@code Count}, {@code Sum}, {@code Min}, {@code Max}, {@code Average}
 * @param groupByExpressions {@code GROUP BY} expressions (not supported by this client)
 * @param hasSelectValue whether the query is {@code SELECT VALUE ...}
 * @param rewrittenQuery the query to send to each partition key range ({@code ""} means the original query)
 * @param queryRanges the effective-partition-key ranges the query targets
 */
public record CosmosQueryPlan(
    String distinctType,
    Integer top,
    Integer offset,
    Integer limit,
    List<String> orderBy,
    List<String> aggregates,
    List<String> groupByExpressions,
    boolean hasSelectValue,
    String rewrittenQuery,
    List<Range> queryRanges) {

  /** Placeholder the gateway puts in rewritten {@code ORDER BY} queries; the first execution replaces it with true. */
  public static final String ORDER_BY_FILTER_PLACEHOLDER = "{documentdb-formattableorderbyquery-filter}";

  /**
   * An effective partition key range.
   *
   * @param min lower bound (hex, {@code ""} is the minimum)
   * @param max upper bound (hex, {@code FF} is the maximum)
   * @param isMinInclusive whether {@code min} is included
   * @param isMaxInclusive whether {@code max} is included
   */
  public record Range(String min, String max, boolean isMinInclusive, boolean isMaxInclusive) {

    /**
     * @param rangeMinInclusive a partition key range's {@code minInclusive}
     * @param rangeMaxExclusive a partition key range's {@code maxExclusive}
     * @return whether that partition key range overlaps this query range
     */
    public boolean overlaps(String rangeMinInclusive, String rangeMaxExclusive) {
      String rangeMin = rangeMinInclusive.toUpperCase(java.util.Locale.ROOT);
      String rangeMax = rangeMaxExclusive.toUpperCase(java.util.Locale.ROOT);
      boolean startsBeforeRangeEnd = min.toUpperCase(java.util.Locale.ROOT).compareTo(rangeMax) < 0;
      int maxVsRangeMin = max.toUpperCase(java.util.Locale.ROOT).compareTo(rangeMin);
      boolean endsAfterRangeStart = isMaxInclusive ? maxVsRangeMin >= 0 : maxVsRangeMin > 0;
      return startsBeforeRangeEnd && endsAfterRangeStart;
    }
  }

  /**
   * @return whether the gateway can serve the query directly (no ordering, aggregation, de-duplication or paging
   *     has to be done by the client)
   */
  public boolean isGatewayServable() {
    return orderBy.isEmpty() && aggregates.isEmpty() && groupByExpressions.isEmpty() && top == null && offset == null
        && limit == null && (distinctType == null || distinctType.equals("None"));
  }

  /**
   * @param originalQuery the query the caller sent
   * @return the query to run against each partition key range
   */
  public String queryForRanges(String originalQuery) {
    String query = rewrittenQuery == null || rewrittenQuery.isBlank() ? originalQuery : rewrittenQuery;
    return query.replace(ORDER_BY_FILTER_PLACEHOLDER, "true");
  }

  /**
   * Parses a query plan response.
   *
   * @param raw the parsed JSON response
   * @return the plan
   */
  @SuppressWarnings("unchecked")
  public static CosmosQueryPlan fromJson(Object raw) {
    Map<String, Object> plan = raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    Map<String, Object> info = plan.get("queryInfo") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    List<Range> ranges = new ArrayList<>();
    if (plan.get("queryRanges") instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> range) {
          ranges.add(new Range(
              string(range.get("min"), ""),
              string(range.get("max"), "FF"),
              !Boolean.FALSE.equals(range.get("isMinInclusive")),
              Boolean.TRUE.equals(range.get("isMaxInclusive"))));
        }
      }
    }
    if (ranges.isEmpty()) {
      ranges.add(new Range("", "FF", true, false));
    }
    return new CosmosQueryPlan(
        string(info.get("distinctType"), "None"),
        integer(info.get("top")),
        integer(info.get("offset")),
        integer(info.get("limit")),
        strings(info.get("orderBy")),
        strings(info.get("aggregates")),
        strings(info.get("groupByExpressions")),
        Boolean.TRUE.equals(info.get("hasSelectValue")),
        string(info.get("rewrittenQuery"), ""),
        List.copyOf(ranges));
  }

  private static String string(Object value, String fallback) {
    return value == null ? fallback : value.toString();
  }

  private static Integer integer(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private static List<String> strings(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object item : list) {
      result.add(String.valueOf(item));
    }
    return List.copyOf(result);
  }
}
