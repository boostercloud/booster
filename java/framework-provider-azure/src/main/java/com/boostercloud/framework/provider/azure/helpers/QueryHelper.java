package com.boostercloud.framework.provider.azure.helpers;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.jsString;
import static com.boostercloud.framework.provider.azure.helpers.JsValues.truthy;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Port of {@code helpers/query-helper.ts}: the generic Cosmos DB SQL builder behind read model searches, event
 * searches, deletable event lookups and the paginated entity ids. Filters ({@code FilterFor} JSON shape) become a
 * {@code WHERE} clause with {@code @placeholders}, sorts ({@code SortFor}) an {@code ORDER BY}, projections
 * ({@code ProjectionFor}) a {@code SELECT} list; results get their dotted aliases re-nested and
 * {@code boosterMetadata.optimisticConcurrencyValue = _etag}.
 *
 * <p>The SQL text, the placeholder names and the parameter order are exactly the TypeScript ones (whitespace
 * included), and so are the quirks documented in the module README (two independent walks for the SQL and the
 * parameters, lower-case {@code and}/{@code or}, verbatim sort directions). One deliberate difference: filter property
 * names are SQL data, never SQL text. They are written as escaped string literals in the {@code ["…"]} accessors and
 * reduced to identifier characters in placeholder names, because the keys of a JSON-typed filter are chosen by the
 * GraphQL client (TypeScript pastes them verbatim, an SQL injection). For identifier-like names the output is
 * unchanged.
 */
public final class QueryHelper {

  /** {@code DEFAULT_PAGE_SIZE}. */
  public static final int DEFAULT_PAGE_SIZE = 100;

  private static final Pattern DISTINCT_QUERY = Pattern.compile("SELECT\\s+DISTINCT\\s+", Pattern.CASE_INSENSITIVE);
  private static final Pattern LEGACY_CURSOR = Pattern.compile("^\\d+$");
  private static final Pattern PLACEHOLDER_UNSAFE = Pattern.compile("[^A-Za-z0-9_]");

  private QueryHelper() {}

  /**
   * {@code replaceOrDeleteItem}: replaces the document when {@code newValue} is given, deletes it otherwise. Errors
   * propagate.
   *
   * @param cosmosDb the client
   * @param container the container name
   * @param config the configuration
   * @param id the document id
   * @param partitionKey its partition key value
   * @param newValue the new document, or {@code null} to delete
   */
  public static void replaceOrDeleteItem(
      CosmosClient cosmosDb,
      String container,
      BoosterConfig config,
      String id,
      String partitionKey,
      Map<String, Object> newValue) {
    var item = cosmosDb.database(config.resourceNames().applicationStack()).container(container).item(id, partitionKey);
    if (newValue != null) {
      item.replace(newValue);
    } else {
      item.delete();
    }
  }

  /**
   * {@code search(cosmosDb, config, containerName, filters)}: non-paginated, no limit, {@code SELECT *}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param containerName the container
   * @param filters the filter
   * @return the processed documents ({@code List<Map<String, Object>>})
   */
  public static Object search(CosmosClient cosmosDb, BoosterConfig config, String containerName, Map<String, ?> filters) {
    return search(cosmosDb, config, containerName, filters, null, null, null, null, null);
  }

  /**
   * {@code search(cosmosDb, config, containerName, filters, limit?, afterCursor?, paginatedVersion = false, order?,
   * projections = '*')}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param containerName the container
   * @param filters the filter ({@code FilterFor} shape; values are converted to raw JSON first, so UUIDs and dates
   *     become strings)
   * @param limit the maximum number of results, or {@code null}
   * @param afterCursor the cursor of the previous page ({@code { id?, continuationToken? }}), or {@code null}
   * @param paginatedVersion {@code true} for the paginated shape; {@code null} means {@code false}
   * @param order the {@code SortFor} map (ordered), or {@code null}
   * @param projections a string projection ({@code "DISTINCT c.entityID"}), a list of {@code ProjectionFor} paths, or
   *     {@code null} for {@code *}
   * @return a {@code List<Map<String, Object>>} when not paginated, a {@code ReadModelListResult<Map<String, Object>>}
   *     otherwise (TypeScript's {@code Array<TResult> | ReadModelListResult<TResult>})
   */
  public static Object search(
      CosmosClient cosmosDb,
      BoosterConfig config,
      String containerName,
      Map<String, ?> filters,
      Integer limit,
      Map<String, ?> afterCursor,
      Boolean paginatedVersion,
      Map<String, ?> order,
      Object projections) {
    Logger logger = Loggers.getLogger(config, "query-helper#search");
    Object rawFilters = BoosterJson.toRaw(filters == null ? Map.of() : filters);
    String filterExpression = buildFilterExpression(rawFilters, new ArrayList<>());
    String projectionsExpression = buildProjections(projections);
    String queryDefinition = "SELECT " + projectionsExpression + " FROM c "
        + (!filterExpression.isEmpty() ? "WHERE " + filterExpression : filterExpression);
    String finalQuery = queryDefinition + buildOrderExpression(order == null ? null : BoosterJson.toRaw(order));

    SqlQuerySpec querySpec = new SqlQuerySpec(finalQuery, buildExpressionAttributeValues(rawFilters, new ArrayList<>()));
    logger.debug("Running search with the following params: \n", BoosterJson.toJson(querySpec));

    CosmosContainer container = cosmosDb.database(config.resourceNames().applicationStack()).container(containerName);

    if (Boolean.TRUE.equals(paginatedVersion)) {
      boolean isDistinctQuery = DISTINCT_QUERY.matcher(finalQuery).find();
      boolean canUseContinuationToken = !isDistinctQuery;
      Object cursorId = afterCursor == null ? null : afterCursor.get("id");
      Object cursorToken = afterCursor == null ? null : afterCursor.get("continuationToken");
      boolean hasLegacyCursor = cursorId instanceof String id && LEGACY_CURSOR.matcher(id).matches();

      String continuationToken = truthy(cursorToken) ? jsString(cursorToken) : null;
      Integer maxItemCount = null;
      if (truthy(limit) || continuationToken != null || canUseContinuationToken) {
        maxItemCount = limit != null ? limit : DEFAULT_PAGE_SIZE;
      }

      if (continuationToken == null && (!canUseContinuationToken || hasLegacyCursor)) {
        double parsedLegacyId = truthy(cursorId) ? JsValues.parseInt(cursorId) : Double.NaN;
        double offset = JsValues.isFinite(parsedLegacyId) ? parsedLegacyId : 0;
        int effectiveLimit = limit != null ? limit : DEFAULT_PAGE_SIZE;
        String legacyQuery = finalQuery + " OFFSET " + BoosterJson.formatNumber(offset) + " LIMIT " + effectiveLimit + " ";
        FeedResponse response = container.items().query(querySpec.withQuery(legacyQuery)).fetchAll();
        List<Map<String, Object>> processedResources = processResources(response.resources());
        Map<String, String> cursor = new LinkedHashMap<>();
        cursor.put("id", BoosterJson.formatNumber(offset + effectiveLimit));
        return new ReadModelListResult<>(processedResources, processedResources.size(), cursor);
      }

      FeedResponse response = container.items()
          .query(querySpec, new FeedOptions(continuationToken, maxItemCount, null))
          .fetchNext();
      List<Map<String, Object>> finalResources = processResources(response.resources());

      double parsedId = truthy(cursorId) ? JsValues.parseInt(cursorId) : Double.NaN;
      double previousOffset = JsValues.isFinite(parsedId) ? parsedId : 0;
      int effectiveLimit = limit != null ? limit : DEFAULT_PAGE_SIZE;

      Map<String, String> cursor = null;
      if (truthy(response.continuationToken())) {
        cursor = new LinkedHashMap<>();
        cursor.put("continuationToken", response.continuationToken());
        cursor.put("id", BoosterJson.formatNumber(previousOffset + effectiveLimit));
      } else if (!finalResources.isEmpty()) {
        cursor = new LinkedHashMap<>();
        cursor.put("id", BoosterJson.formatNumber(previousOffset + effectiveLimit));
      }
      return new ReadModelListResult<>(finalResources, finalResources.size(), cursor);
    }

    String finalQueryForNonPaginated = finalQuery;
    if (truthy(limit)) {
      finalQueryForNonPaginated += " OFFSET 0 LIMIT " + limit + " ";
    }
    FeedResponse response = container.items().query(querySpec.withQuery(finalQueryForNonPaginated)).fetchAll();
    return processResources(response.resources());
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Result processing

  /**
   * {@code processResources}: re-nests dotted aliases ({@link #nestProperties(Object)}) and sets
   * {@code boosterMetadata = { ...row.boosterMetadata, optimisticConcurrencyValue: row._etag }} on every row (the
   * property is left out when the row has no {@code _etag}, which is how {@code undefined} serialises).
   *
   * @param resources the raw rows
   * @return the processed rows
   */
  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> processResources(List<Object> resources) {
    Object nested = nestProperties(resources);
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object resource : (List<Object>) nested) {
      Map<String, Object> row = resource instanceof Map<?, ?> map
          ? new LinkedHashMap<>((Map<String, Object>) map)
          : new LinkedHashMap<>();
      Map<String, Object> metadata = row.get("boosterMetadata") instanceof Map<?, ?> existing
          ? new LinkedHashMap<>((Map<String, Object>) existing)
          : new LinkedHashMap<>();
      if (row.containsKey("_etag")) {
        metadata.put("optimisticConcurrencyValue", row.get("_etag"));
      } else {
        metadata.remove("optimisticConcurrencyValue");
      }
      row.put("boosterMetadata", metadata);
      result.add(row);
    }
    return result;
  }

  /**
   * {@code nestProperties}: for every element of an array, rebuilds each object splitting its top-level keys on
   * {@code .} ({@code {"a.b": 1}} becomes {@code {a: {b: 1}}}); nested values are not visited, arrays are processed
   * recursively and primitives returned as they are. A single object is nested itself; anything else gives
   * {@code {}}.
   *
   * @param value the rows
   * @return the nested rows
   */
  public static Object nestProperties(Object value) {
    if (value instanceof List<?> list) {
      return processArray(list);
    }
    Map<String, Object> result = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> map) {
      processObject(map, result);
    }
    return result;
  }

  private static List<Object> processArray(List<?> array) {
    List<Object> result = new ArrayList<>();
    for (Object item : array) {
      if (item instanceof List<?> nested) {
        result.add(processArray(nested));
      } else if (item instanceof Map<?, ?> map) {
        Map<String, Object> nestedItem = new LinkedHashMap<>();
        processObject(map, nestedItem);
        result.add(nestedItem);
      } else {
        result.add(item);
      }
    }
    return result;
  }

  private static void processObject(Map<?, ?> input, Map<String, Object> output) {
    for (Map.Entry<?, ?> entry : input.entrySet()) {
      String key = String.valueOf(entry.getKey());
      setNestedProperty(output, jsSplit(key, "."), entry.getValue());
    }
  }

  @SuppressWarnings("unchecked")
  private static void setNestedProperty(Map<String, Object> object, List<String> path, Object value) {
    Object current = object;
    for (int i = 0; i < path.size() - 1; i++) {
      Map<String, Object> currentMap = asObject(current, path.get(i));
      if (!truthy(currentMap.get(path.get(i)))) {
        currentMap.put(path.get(i), new LinkedHashMap<String, Object>());
      }
      current = currentMap.get(path.get(i));
    }
    asObject(current, path.getLast()).put(path.getLast(), value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object current, String key) {
    if (current instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalStateException("Cannot create property '" + key + "' on " + jsString(current));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // WHERE

  /**
   * {@code buildFilterExpression}: the {@code WHERE} expression of a filter.
   *
   * @param filters the raw filter
   * @param usedPlaceholders placeholders already taken (shared by the whole walk)
   * @return the expression ({@code ""} for an empty filter)
   */
  @SuppressWarnings("unchecked")
  static String buildFilterExpression(Object filters, List<String> usedPlaceholders) {
    List<String> parts = new ArrayList<>();
    for (Map.Entry<String, Object> entry : entries(filters)) {
      String propName = entry.getKey();
      Object filter = entry.getValue();
      switch (propName) {
        case "not" -> parts.add("NOT (" + buildFilterExpression(filter, usedPlaceholders) + ")");
        case "and", "or" -> {
          List<String> nested = new ArrayList<>();
          for (Object arrayFilter : iterable(filter)) {
            nested.add(buildFilterExpression(arrayFilter, usedPlaceholders));
          }
          parts.add("(" + String.join(" " + propName + " ", nested) + ")");
        }
        default -> parts.add(buildOperation(propName, filter, usedPlaceholders, null));
      }
    }
    return String.join(" AND ", parts);
  }

  private static String buildOperation(String propName, Object filter, List<String> usedPlaceholders, String nested) {
    BiFunction<Integer, Integer, String> holder = placeholderBuilderFor(propName, usedPlaceholders);
    String property = (nested != null ? nested : "c") + "[" + propertyLiteral(propName) + "]";
    List<String> parts = new ArrayList<>();
    int index = 0;
    for (Map.Entry<String, Object> entry : entries(filter)) {
      String operation = entry.getKey();
      Object value = entry.getValue();
      int i = index++;
      parts.add(switch (operation) {
        case "eq" -> property + " = " + holder.apply(i, null);
        case "ne" -> property + " <> " + holder.apply(i, null);
        case "lt" -> property + " < " + holder.apply(i, null);
        case "gt" -> property + " > " + holder.apply(i, null);
        case "gte" -> property + " >= " + holder.apply(i, null);
        case "lte" -> property + " <= " + holder.apply(i, null);
        case "in" -> {
          if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("value.map is not a function");
          }
          List<String> placeholders = new ArrayList<>();
          for (int subIndex = 0; subIndex < values.size(); subIndex++) {
            placeholders.add(holder.apply(i, subIndex));
          }
          yield property + " IN (" + String.join(",", placeholders) + ")";
        }
        case "contains" -> "CONTAINS(" + property + ", " + holder.apply(i, null) + ")";
        case "beginsWith" -> "STARTSWITH(" + property + ", " + holder.apply(i, null) + ")";
        case "includes" -> "ARRAY_CONTAINS(" + property + ", " + holder.apply(i, null) + ", true)";
        case "isDefined" -> truthy(value) ? "IS_DEFINED(" + property + ")" : "NOT IS_DEFINED(" + property + ")";
        case "regex" -> "RegexMatch(" + property + ", " + holder.apply(i, null) + ")";
        case "iRegex" -> "RegexMatch(" + property + ", " + holder.apply(i, null) + ", 'i')";
        default -> {
          if (isJsObject(value)) {
            yield buildOperation(operation, value, usedPlaceholders, property);
          }
          throw new InvalidParameterError("Operator \"" + operation + "\" is not supported");
        }
      });
    }
    return String.join(" AND ", parts);
  }

  /**
   * The property name as a Cosmos DB SQL string literal for the {@code ["…"]} accessor: JSON string escaping, which
   * the Cosmos DB grammar accepts (escaped quote, backslash and control characters). For the names TypeScript
   * handles correctly this is exactly {@code "<name>"}. TypeScript pastes the name verbatim, so a filter key chosen by
   * a GraphQL client (the keys of a JSON-typed filter) could close the accessor and inject SQL (README, "Differences
   * from TypeScript").
   */
  private static String propertyLiteral(String propName) {
    return BoosterJson.toJson(propName);
  }

  /**
   * {@code placeholderBuilderFor}: {@code @<prop>_<index>[_<subIndex>]}; on a collision it retries with
   * {@code index + 1} and no sub-index. Every character of {@code <prop>} other than an ASCII letter, digit or
   * {@code _} is written as {@code _} (TypeScript pastes the name verbatim: an injection point, and an invalid
   * parameter name for any such character); collisions this creates are resolved by the retry rule, identically in
   * the SQL walk and the parameter walk.
   */
  private static BiFunction<Integer, Integer, String> placeholderBuilderFor(String propName, List<String> used) {
    String safeName = PLACEHOLDER_UNSAFE.matcher(propName).replaceAll("_");
    return new BiFunction<>() {
      @Override
      public String apply(Integer valueIndex, Integer valueSubIndex) {
        String placeholder = "@" + safeName + "_" + valueIndex + (valueSubIndex != null ? "_" + valueSubIndex : "");
        if (used.contains(placeholder)) {
          return placeholderBuilderFor(propName, used).apply(valueIndex + 1, null);
        }
        used.add(placeholder);
        return placeholder;
      }
    };
  }

  /**
   * {@code buildExpressionAttributeValues}: the parameters of a filter, in the order of the SQL placeholders.
   *
   * @param filters the raw filter
   * @param usedPlaceholders placeholders already taken (a fresh list, independent of the SQL walk)
   * @return the parameters
   */
  static List<SqlParameter> buildExpressionAttributeValues(Object filters, List<String> usedPlaceholders) {
    List<SqlParameter> attributeValues = new ArrayList<>();
    for (Map.Entry<String, Object> entry : entries(filters)) {
      String propName = entry.getKey();
      switch (propName) {
        case "not" -> attributeValues.addAll(buildExpressionAttributeValues(entry.getValue(), usedPlaceholders));
        case "and", "or" -> {
          for (Object filter : iterable(entry.getValue())) {
            attributeValues.addAll(buildExpressionAttributeValues(filter, usedPlaceholders));
          }
        }
        default -> attributeValues.addAll(buildAttributeValue(propName, entry.getValue(), usedPlaceholders));
      }
    }
    return attributeValues;
  }

  private static List<SqlParameter> buildAttributeValue(String propName, Object filter, List<String> usedPlaceholders) {
    List<SqlParameter> attributeValues = new ArrayList<>();
    BiFunction<Integer, Integer, String> holder = placeholderBuilderFor(propName, usedPlaceholders);
    int index = 0;
    for (Map.Entry<String, Object> entry : entries(filter)) {
      String key = entry.getKey();
      Object value = entry.getValue();
      int i = index++;
      if (value instanceof List<?> list) {
        for (int subIndex = 0; subIndex < list.size(); subIndex++) {
          attributeValues.add(new SqlParameter(holder.apply(i, subIndex), list.get(subIndex)));
        }
      } else if (isJsObject(value) && !key.equals("includes") && value != null) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put(key, value);
        attributeValues.addAll(buildExpressionAttributeValues(nested, usedPlaceholders));
      } else if (!key.equals("isDefined")) {
        attributeValues.add(new SqlParameter(holder.apply(i, null), value));
      }
    }
    return attributeValues;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // ORDER BY

  /**
   * {@code buildOrderExpression}: {@code " ORDER BY c.a DESC, c.b.c ASC"} (dot notation, directions verbatim), or
   * {@code ""} without sort.
   *
   * @param sortFor the raw sort, or {@code null}
   * @return the clause
   */
  static String buildOrderExpression(Object sortFor) {
    if (!truthy(sortFor) || keys(sortFor).isEmpty()) {
      return "";
    }
    List<String> sorted = toLocalSortFor(sortFor, "", new ArrayList<>());
    return " ORDER BY " + (sorted == null ? "undefined" : String.join(", ", sorted));
  }

  private static List<String> toLocalSortFor(Object sortBy, String parentKey, List<String> sortedList) {
    if (!truthy(sortBy) || keys(sortBy).isEmpty()) {
      return null;
    }
    for (Map.Entry<String, Object> entry : entries(sortBy)) {
      if (entry.getValue() instanceof String direction) {
        sortedList.add("c." + parentKey + entry.getKey() + " " + direction);
      } else {
        toLocalSortFor(entry.getValue(), parentKey + entry.getKey() + ".", sortedList);
      }
    }
    return sortedList;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // SELECT

  /**
   * {@code buildProjections}: the {@code SELECT} list. A non-object projection (a string) is used verbatim;
   * {@code null} is {@code *}.
   *
   * @param projections the projection
   * @return the select list
   */
  static String buildProjections(Object projections) {
    if (projections == null) {
      return "*";
    }
    if (!isJsObject(projections)) {
      return jsString(projections);
    }
    List<String> preprocessedProjections = preprocessProjections(projections);

    Map<String, List<String>> groupedFields = new LinkedHashMap<>();
    for (String field : preprocessedProjections) {
      String root = jsSplit(field, ".").getFirst();
      groupedFields.computeIfAbsent(root, ignored -> new ArrayList<>()).add(field);
    }

    List<String> selects = new ArrayList<>();
    for (Map.Entry<String, List<String>> group : groupedFields.entrySet()) {
      String root = group.getKey();
      List<String> fields = group.getValue();
      if (root.endsWith("[]")) {
        String arrayRoot = root.substring(0, root.length() - 2);
        List<String> subFields = new ArrayList<>();
        for (String field : fields) {
          subFields.add("item" + toSquareBracketsNotation(replaceFirst(field, root + ".", "")));
        }
        selects.add("ARRAY(SELECT " + String.join(", ", subFields) + " FROM item IN c[\"" + arrayRoot + "\"]) AS "
            + arrayRoot);
      } else if (fields.size() == 1 && !fields.getFirst().contains(".")) {
        selects.add("c" + toSquareBracketsNotation(fields.getFirst()));
      } else {
        Map<String, List<String>> nestedFields = new LinkedHashMap<>();
        for (String field : fields) {
          List<String> parts = jsSplit(field, ".");
          List<String> rest = parts.subList(1, parts.size());
          if (!rest.isEmpty()) {
            nestedFields.computeIfAbsent(rest.getFirst(), ignored -> new ArrayList<>()).add(String.join(".", rest));
          }
        }
        List<String> nestedSelects = new ArrayList<>();
        for (Map.Entry<String, List<String>> nested : nestedFields.entrySet()) {
          String nestedRoot = nested.getKey();
          if (nestedRoot.endsWith("[]")) {
            String arrayNestedRoot = nestedRoot.substring(0, nestedRoot.length() - 2);
            List<String> subArrayFields = new ArrayList<>();
            for (String field : nested.getValue()) {
              List<String> parts = jsSplit(field, ".");
              String subFieldParts = String.join(".", parts.subList(1, parts.size()));
              subArrayFields.add("item" + toSquareBracketsNotation(subFieldParts));
            }
            nestedSelects.add("ARRAY(SELECT " + String.join(", ", subArrayFields) + " FROM item IN c"
                + toSquareBracketsNotation(root + "." + arrayNestedRoot) + ") AS \"" + root + "." + arrayNestedRoot
                + "\"");
          } else {
            List<String> subFields = new ArrayList<>();
            for (String field : nested.getValue()) {
              subFields.add("c" + toSquareBracketsNotation(root + "." + field) + " AS \"" + root + "." + field + "\"");
            }
            nestedSelects.add(String.join(", ", subFields));
          }
        }
        selects.add(String.join(", ", nestedSelects));
      }
    }
    return String.join(", ", selects);
  }

  /**
   * {@code preprocessProjections}: keeps fields with no array segment or a single one at index 0 or 1; otherwise
   * truncates the path at the second array segment (when the first is at index 0 or 1) or at the first one, and drops
   * that segment's {@code []}. Duplicates are removed keeping the first occurrence.
   */
  private static List<String> preprocessProjections(Object projections) {
    Set<String> processed = new LinkedHashSet<>();
    for (Object rawField : values(projections)) {
      String field = jsString(rawField);
      List<String> parts = jsSplit(field, ".");
      List<Integer> arrayIndices = new ArrayList<>();
      for (int index = 0; index < parts.size(); index++) {
        if (parts.get(index).endsWith("[]")) {
          arrayIndices.add(index);
        }
      }
      if (arrayIndices.isEmpty()
          || (arrayIndices.getFirst() == 0 && arrayIndices.size() == 1)
          || (arrayIndices.getFirst() == 1 && arrayIndices.size() == 1)) {
        processed.add(field);
      } else {
        int processToIndex = arrayIndices.getFirst() == 0 || arrayIndices.getFirst() == 1
            ? arrayIndices.get(1)
            : arrayIndices.getFirst();
        String processedField = String.join(".", parts.subList(0, processToIndex + 1));
        processed.add(processedField.substring(0, processedField.length() - 2));
      }
    }
    return new ArrayList<>(processed);
  }

  private static String toSquareBracketsNotation(String path) {
    StringBuilder result = new StringBuilder();
    for (String part : jsSplit(path, ".")) {
      result.append("[\"").append(part).append("\"]");
    }
    return result.toString();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // JavaScript object helpers

  /** {@code Object.entries(value)}; {@code null} throws like JavaScript. */
  @SuppressWarnings("unchecked")
  private static List<Map.Entry<String, Object>> entries(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("Cannot convert undefined or null to object");
    }
    List<Map.Entry<String, Object>> result = new ArrayList<>();
    if (value instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        result.add(new AbstractMap.SimpleEntry<>(String.valueOf(entry.getKey()), entry.getValue()));
      }
    } else if (value instanceof List<?> list) {
      for (int i = 0; i < list.size(); i++) {
        result.add(new AbstractMap.SimpleEntry<>(String.valueOf(i), list.get(i)));
      }
    } else if (value instanceof String text) {
      for (int i = 0; i < text.length(); i++) {
        result.add(new AbstractMap.SimpleEntry<>(String.valueOf(i), String.valueOf(text.charAt(i))));
      }
    }
    return result;
  }

  private static List<String> keys(Object value) {
    return entries(value).stream().map(Map.Entry::getKey).toList();
  }

  private static List<Object> values(Object value) {
    return entries(value).stream().map(Map.Entry::getValue).toList();
  }

  /** {@code for (const x of value)}: arrays (and strings) are iterable, anything else throws. */
  private static List<Object> iterable(Object value) {
    if (value instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    if (value instanceof String) {
      return values(value);
    }
    throw new IllegalArgumentException(jsString(value) + " is not iterable");
  }

  /** {@code typeof value === 'object'}: objects, arrays and {@code null}. */
  private static boolean isJsObject(Object value) {
    return value == null || value instanceof Map<?, ?> || value instanceof List<?>;
  }

  /** {@code text.split(separator)} keeping trailing empty strings. */
  private static List<String> jsSplit(String text, String separator) {
    return List.of(text.split(Pattern.quote(separator), -1));
  }

  /** {@code text.replace(search, replacement)} with a string pattern: the first occurrence only. */
  private static String replaceFirst(String text, String search, String replacement) {
    int index = text.indexOf(search);
    return index < 0 ? text : text.substring(0, index) + replacement + text.substring(index + search.length());
  }
}
